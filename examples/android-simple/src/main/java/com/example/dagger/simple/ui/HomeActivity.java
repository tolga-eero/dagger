/*
 * Copyright (C) 2013 Square, Inc.
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
package com.example.dagger.simple.ui;

import android.location.LocationManager;
import android.os.Bundle;
import com.example.dagger.simple.DemoBaseActivity;
import javax.inject.InjectDagger1;

public class HomeActivity extends DemoBaseActivity {
  @InjectDagger1 LocationManager locationManager;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // After the super.onCreate call returns we are guaranteed our injections are available.

    // TODO do something with the injected dependencies here!
  }
}
