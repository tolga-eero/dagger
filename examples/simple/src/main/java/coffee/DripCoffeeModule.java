package coffee;

import dagger.ModuleDagger1;
import dagger.ProvidesDagger1;

import javax.inject.Singleton;

@ModuleDagger1(
    injects = CoffeeApp.class,
    includes = PumpModule.class
)
class DripCoffeeModule {
  @ProvidesDagger1
  @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }
}
