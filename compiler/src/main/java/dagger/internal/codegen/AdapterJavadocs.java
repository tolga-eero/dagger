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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

/**
 * Utility class providing some commonly used boilerplate between {@code InjectAdapterProcessor}
 * and {@code ModuleAdapterProcessor}.
 */
public final class AdapterJavadocs {
  static final String GENERATED_BY_DAGGER = "Code generated by dagger-compiler.  Do not edit.";
  static final String MEMBERS_INJECT_METHOD = ""
      + "Injects any {@code @InjectDagger1} annotated fields in the given instance,\n"
      + "satisfying the contract for {@code ProviderDagger1<$T>}.\n";
  static final String GET_METHOD = ""
      + "Returns the fully provisioned instance satisfying the contract for\n"
      + "{@code ProviderDagger1<$T>}.\n";
  static final String GET_DEPENDENCIES_METHOD = ""
      + "Used internally obtain dependency information, such as for cyclical\n"
      + "graph detection.\n";
  static final String ATTACH_METHOD = ""
      + "Used internally to link bindings/providers together at run time\n"
      + "according to their dependency graph.\n";
  static final String STATIC_INJECT_METHOD = ""
      + "Performs the injections of dependencies into static fields when requested by\n"
      + "the {@code $T}.\n";
  static final String MODULE_TYPE = ""
      + "A manager of modules and provides adapters allowing for proper linking and\n"
      + "instance provision of types served by {@code @$T} methods.\n";
  static final String STATIC_INJECTION_TYPE = ""
      + "A manager for {@code $T}'s injections into static fields.\n";

  /** Creates an appropriate javadoc depending on aspects of the type in question. */
  static CodeBlock bindingTypeDocs(
      TypeName type, boolean abstrakt, boolean members, boolean dependent) {
    CodeBlock.Builder result = CodeBlock.builder()
        .add("A {@code Binding<$T>} implementation which satisfies\n", type)
        .add("Dagger's infrastructure requirements including:\n");
    if (dependent) {
      result.add("\n")
          .add("Owning the dependency links between {@code $T} and its\n", type)
          .add("dependencies.\n");
    }
    if (!abstrakt) {
      result.add("\n")
          .add("Being a {@code ProviderDagger1<$T>} and handling creation and\n", type)
          .add("preparation of object instances.\n");
    }
    if (members) {
      result.add("\n")
          .add("Being a {@code MembersInjector<$T>} and handling injection\n", type)
          .add("of annotated fields.\n");
    }
    return result.build();
  }
}
