package coffee;

import javax.inject.InjectDagger1;

class Thermosiphon implements Pump {
  private final Heater heater;

  @InjectDagger1
  Thermosiphon(Heater heater) {
    this.heater = heater;
  }

  @Override public void pump() {
    if (heater.isHot()) {
      System.out.println("=> => pumping => =>");
    }
  }
}
