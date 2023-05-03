package coffee;

import dagger.ModuleDagger1;
import dagger.ProvidesDagger1;

import javax.inject.SingletonDagger1;

@ModuleDagger1(
    injects = CoffeeApp.class,
    includes = PumpModule.class
)
class DripCoffeeModule {
  @ProvidesDagger1
  @SingletonDagger1 Heater provideHeater() {
    return new ElectricHeater();
  }
}
