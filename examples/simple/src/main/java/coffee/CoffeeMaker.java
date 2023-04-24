package coffee;

import dagger.LazyDagger1;
import javax.inject.Inject;

class CoffeeMaker {
  @Inject LazyDagger1<Heater> heater;
  // Don't want to create a possibly costly heater until we need it.
  @Inject Pump pump;

  public void brew() {
    heater.get().on();
    pump.pump();
    System.out.println(" [_]P coffee! [_]P ");
    heater.get().off();
  }
}
