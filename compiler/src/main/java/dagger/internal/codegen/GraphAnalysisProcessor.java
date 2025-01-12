/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.internal.codegen;

import dagger.ModuleDagger1;
import dagger.ProvidesDagger1;
import dagger.internal.Binding;
import dagger.internal.Binding.InvalidBindingException;
import dagger.internal.BindingsGroup;
import dagger.internal.Linker;
import dagger.internal.ProblemDetector;
import dagger.internal.ProvidesBinding;
import dagger.internal.SetBinding;
import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.SingletonDagger1;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

import static dagger.ProvidesDagger1.Type.SET;
import static dagger.ProvidesDagger1.Type.SET_VALUES;
import static dagger.internal.codegen.Util.className;
import static dagger.internal.codegen.Util.getAnnotation;
import static dagger.internal.codegen.Util.getPackage;
import static dagger.internal.codegen.Util.isInterface;
import static java.util.Arrays.asList;

/**
 * Performs full graph analysis on a module.
 */
@SupportedAnnotationTypes("dagger.Module")
public final class GraphAnalysisProcessor extends AbstractProcessor {
  private static final Set<String> ERROR_NAMES_TO_PROPAGATE = new LinkedHashSet<String>(asList(
      "com.sun.tools.javac.code.Symbol$CompletionFailure"));

  private final Set<String> delayedModuleNames = new LinkedHashSet<String>();

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Perform full-graph analysis on complete modules. This checks that all of
   * the module's dependencies are satisfied.
   */
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    if (!env.processingOver()) {
      // Storing module names for later retrieval as the element instance is invalidated across
      // passes.
      for (Element e : env.getElementsAnnotatedWith(ModuleDagger1.class)) {
        if (!(e instanceof TypeElement)) {
          error("@Module applies to a type, " + e.getSimpleName() + " is a " + e.getKind(), e);
          continue;
        }
        delayedModuleNames.add(((TypeElement) e).getQualifiedName().toString());
      }
      return false;
    }

    Set<Element> modules = new LinkedHashSet<Element>();
    for (String moduleName : delayedModuleNames) {
      modules.add(elements().getTypeElement(moduleName));
    }

    for (Element element : modules) {
      Map<String, Object> annotation = null;
      try {
        annotation = getAnnotation(ModuleDagger1.class, element);
      } catch (CodeGenerationIncompleteException e) {
        continue; // skip this element. An up-stream compiler error is in play.
      }

      TypeElement moduleType = (TypeElement) element;
      if (annotation == null) {
        error("Missing @Module annotation.", moduleType);
        continue;
      }
      if (annotation.get("complete").equals(Boolean.TRUE)) {
        Map<String, Binding<?>> bindings;
        try {
          bindings = processCompleteModule(moduleType, false);
          new ProblemDetector().detectCircularDependencies(bindings.values());
        } catch (ModuleValidationException e) {
          error("Graph validation failed: " + e.getMessage(), e.source);
          continue;
        } catch (InvalidBindingException e) {
          error("Graph validation failed: " + e.getMessage(), elements().getTypeElement(e.type));
          continue;
        } catch (RuntimeException e) {
          if (ERROR_NAMES_TO_PROPAGATE.contains(e.getClass().getName())) {
            throw e;
          }
          error("Unknown error " + e.getClass().getName() + " thrown by javac in graph validation: "
              + e.getMessage(), moduleType);
          continue;
        }
        try {
          writeDotFile(moduleType, bindings);
        } catch (IOException e) {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          processingEnv.getMessager()
              .printMessage(Diagnostic.Kind.WARNING,
                  "Graph visualization failed. Please report this as a bug.\n\n" + sw, moduleType);
        }
      }

      if (annotation.get("library").equals(Boolean.FALSE)) {
        Map<String, Binding<?>> bindings = processCompleteModule(moduleType, true);
        try {
          new ProblemDetector().detectUnusedBinding(bindings.values());
        } catch (IllegalStateException e) {
          error("Graph validation failed: " + e.getMessage(), moduleType);
        }
      }
    }
    return false;
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  private Map<String, Binding<?>> processCompleteModule(TypeElement rootModule,
      boolean ignoreCompletenessErrors) {
    Map<String, TypeElement> allModules = new LinkedHashMap<String, TypeElement>();
    collectIncludesRecursively(rootModule, allModules, new LinkedList<String>());
    ArrayList<GraphAnalysisStaticInjection> staticInjections =
        new ArrayList<GraphAnalysisStaticInjection>();

    Linker.ErrorHandler errorHandler = ignoreCompletenessErrors ? Linker.ErrorHandler.NULL
        : new GraphAnalysisErrorHandler(processingEnv, rootModule.getQualifiedName().toString());
    Linker linker = new Linker(null, new GraphAnalysisLoader(processingEnv), errorHandler);
    // Linker requires synchronization for calls to requestBinding and linkAll.
    // We know statically that we're single threaded, but we synchronize anyway
    // to make the linker happy.
    synchronized (linker) {
      BindingsGroup baseBindings = new BindingsGroup() {
        @Override public Binding<?> contributeSetBinding(String key, SetBinding<?> value) {
          return super.put(key, value);
        }
      };
      BindingsGroup overrideBindings = new BindingsGroup() {
        @Override public Binding<?> contributeSetBinding(String key, SetBinding<?> value) {
          throw new IllegalStateException("Module overrides cannot contribute set bindings.");
        }
      };
      for (TypeElement module : allModules.values()) {
        Map<String, Object> annotation = getAnnotation(ModuleDagger1.class, module);
        boolean overrides = (Boolean) annotation.get("overrides");
        boolean library = (Boolean) annotation.get("library");
        BindingsGroup addTo = overrides ? overrideBindings : baseBindings;

        // Gather the injectable types from the annotation.
        Set<String> injectsProvisionKeys = new LinkedHashSet<String>();
        for (Object injectableTypeObject : (Object[]) annotation.get("injects")) {
          TypeMirror injectableType = (TypeMirror) injectableTypeObject;
          String providerKey = GeneratorKeys.get(injectableType);
          injectsProvisionKeys.add(providerKey);
          String key = isInterface(injectableType)
              ? providerKey
              : GeneratorKeys.rawMembersKey(injectableType);
          linker.requestBinding(key, module.getQualifiedName().toString(),
              getClass().getClassLoader(), false, true);
        }

        // Gather the static injections.
        for (Object staticInjection : (Object[]) annotation.get("staticInjections")) {
          TypeMirror staticInjectionTypeMirror = (TypeMirror) staticInjection;
          Element element = processingEnv.getTypeUtils().asElement(staticInjectionTypeMirror);
          staticInjections.add(new GraphAnalysisStaticInjection(element));
        }

        // Gather the enclosed @Provides methods.
        for (Element enclosed : module.getEnclosedElements()) {
          ProvidesDagger1 providesDagger1 = enclosed.getAnnotation(ProvidesDagger1.class);
          if (providesDagger1 == null) {
            continue;
          }
          ExecutableElement providerMethod = (ExecutableElement) enclosed;
          String key = GeneratorKeys.get(providerMethod);
          ProvidesBinding<?> binding = new ProviderMethodBinding(key, providerMethod, library);

          Binding<?> previous = addTo.get(key);
          if (previous != null) {
            if ((providesDagger1.type() == SET || providesDagger1.type() == SET_VALUES)
                && previous instanceof SetBinding) {
              // No duplicate bindings error if both bindings are set bindings.
            } else {
              String message = "Duplicate bindings for " + key;
              if (overrides) {
                message += " in override module(s) - cannot override an override";
              }
              message += ":\n    " + previous.requiredBy + "\n    " + binding.requiredBy;
              error(message, providerMethod);
            }
          }

          switch (providesDagger1.type()) {
            case UNIQUE:
              if (injectsProvisionKeys.contains(binding.provideKey)) {
                binding.setDependedOn(true);
              }
              try {
                addTo.contributeProvidesBinding(key, binding);
              } catch (IllegalStateException ise) {
                throw new ModuleValidationException(ise.getMessage(), providerMethod);
              }
              break;

            case SET:
              String setKey = GeneratorKeys.getSetKey(providerMethod);
              SetBinding.add(addTo, setKey, binding);
              break;

            case SET_VALUES:
              SetBinding.add(addTo, key, binding);
              break;

            default:
              throw new AssertionError("Unknown @Provides type " + providesDagger1.type());
          }
        }
      }

      linker.installBindings(baseBindings);
      linker.installBindings(overrideBindings);
      for (GraphAnalysisStaticInjection staticInjection : staticInjections) {
        staticInjection.attach(linker);
      }

      // Link the bindings. This will traverse the dependency graph, and report
      // errors if any dependencies are missing.
      return linker.linkAll();
    }
  }

  private Elements elements() {
    return processingEnv.getElementUtils();
  }

  void collectIncludesRecursively(
      TypeElement module, Map<String, TypeElement> result, Deque<String> path) {
    Map<String, Object> annotation = getAnnotation(ModuleDagger1.class, module);
    if (annotation == null) {
      // TODO(tbroyer): pass annotation information
      throw new ModuleValidationException("No @Module on " + module, module);
    }

    // Add the module.
    String name = module.getQualifiedName().toString();
    if (path.contains(name)) {
      StringBuilder message = new StringBuilder("Module Inclusion Cycle: ");
      if (path.size() == 1) {
        message.append(name).append(" includes itself directly.");
      } else {
        String current = null;
        String includer = name;
        for (int i = 0; path.size() > 0; i++) {
          current = includer;
          includer = path.pop();
          message.append("\n").append(i).append(". ")
              .append(current).append(" included by ").append(includer);
        }
        message.append("\n0. ").append(name);
      }
      throw new ModuleValidationException(message.toString(), module);
    }
    result.put(name, module);

    // Recurse for each included module.
    Types types = processingEnv.getTypeUtils();
    List<Object> seedModules = new ArrayList<Object>();
    seedModules.addAll(Arrays.asList((Object[]) annotation.get("includes")));
    if (!annotation.get("addsTo").equals(Void.class)) seedModules.add(annotation.get("addsTo"));
    for (Object include : seedModules) {
      if (!(include instanceof TypeMirror)) {
        // TODO(tbroyer): pass annotation information
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value for include: " + include + " in " + module, module);
        continue;
      }
      TypeElement includedModule = (TypeElement) types.asElement((TypeMirror) include);
      path.push(name);
      collectIncludesRecursively(includedModule, result, path);
      path.pop();
    }
  }

  static class ProviderMethodBinding extends ProvidesBinding<Object> {
    private final ExecutableElement method;
    private final Binding<?>[] parameters;

    protected ProviderMethodBinding(String provideKey, ExecutableElement method, boolean library) {
      super(provideKey, method.getAnnotation(SingletonDagger1.class) != null,
          className(method), method.getSimpleName().toString());
      this.method = method;
      this.parameters = new Binding[method.getParameters().size()];
      setLibrary(library);
    }

    @Override public void attach(Linker linker) {
      for (int i = 0; i < method.getParameters().size(); i++) {
        VariableElement parameter = method.getParameters().get(i);
        String parameterKey = GeneratorKeys.get(parameter);
        parameters[i] = linker.requestBinding(parameterKey, method.toString(),
            getClass().getClassLoader());
      }
    }

    @Override public Object get() {
      throw new AssertionError("Compile-time binding should never be called to inject.");
    }

    @Override public void injectMembers(Object t) {
      throw new AssertionError("Compile-time binding should never be called to inject.");
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      Collections.addAll(get, parameters);
    }

    @Override public String toString() {
      return "ProvidesBinding[key=" + provideKey
          + " method=" + moduleClass + "." + method.getSimpleName() + "()";
    }
  }

  void writeDotFile(TypeElement module, Map<String, Binding<?>> bindings) throws IOException {
    JavaFileManager.Location location = StandardLocation.SOURCE_OUTPUT;
    String path = getPackage(module).getQualifiedName().toString();
    String file = module.getQualifiedName().toString().substring(path.length() + 1) + ".dot";
    FileObject resource = processingEnv.getFiler().createResource(location, path, file, module);

    Writer writer = resource.openWriter();
    GraphVizWriter dotWriter = new GraphVizWriter(writer);
    new GraphVisualizer().write(bindings, dotWriter);
    dotWriter.close();
  }

  static class ModuleValidationException extends IllegalStateException {
    final Element source;

    public ModuleValidationException(String message, Element source) {
      super(message);
      this.source = source;
    }
  }
}
