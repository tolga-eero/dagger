package coffee;

import dagger.ModuleDagger1;
import dagger.ProvidesDagger1;

@ModuleDagger1(complete = false, library = true)
class PumpModule {
  @ProvidesDagger1
  Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
