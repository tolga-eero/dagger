package coffee;

import dagger.LazyDagger1;
import javax.inject.InjectDagger1;

class CoffeeMaker {
  @InjectDagger1 LazyDagger1<Heater> heater;
  // Don't want to create a possibly costly heater until we need it.
  @InjectDagger1 Pump pump;

  public void brew() {
    heater.get().on();
    pump.pump();
    System.out.println(" [_]P coffee! [_]P ");
    heater.get().off();
  }
}
