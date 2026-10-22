# Third-Party Software Notices

DownFlow incorporates the following open-source software. Their licenses
are reproduced below as required.

---

## aria2

**Website:** https://aria2.github.io  
**Source:** https://github.com/aria2/aria2  
**Version:** 1.37.0 (or later, as built by `scripts/build_aria2_android.sh`)  
**License:** GNU General Public License, Version 2 or later (GPL-2.0+)

DownFlow embeds an aria2c binary as an asset within the APK. This makes
DownFlow a work that uses aria2 as a subprocess. In accordance with the
GPL:

- The full source code of aria2 is available at its official GitHub
  repository: https://github.com/aria2/aria2
- The build script used to compile the ARM64 binary is included in this
  repository at `scripts/build_aria2_android.sh`.
- DownFlow does not modify the aria2 source code.

aria2 is copyright (c) 2006–2016 Tatsuhiro Tsujikawa.

The text of the GPL v2 can be found at:
https://www.gnu.org/licenses/old-licenses/gpl-2.0.html

---

## OkHttp

**Website:** https://square.github.io/okhttp/  
**Source:** https://github.com/square/okhttp  
**License:** Apache License, Version 2.0

Copyright 2019 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

---

## Gson

**Website:** https://github.com/google/gson  
**License:** Apache License, Version 2.0

Copyright 2008 Google Inc.

Licensed under the Apache License, Version 2.0.

---

## Jetpack (AndroidX) Libraries

Including Compose, Hilt Navigation Compose, Room, DataStore, Navigation,
ViewModel, Lifecycle, Activity Compose, Security Crypto.

**Source:** https://android.googlesource.com/platform/frameworks/support  
**License:** Apache License, Version 2.0

Copyright (C) The Android Open Source Project

Licensed under the Apache License, Version 2.0.

---

## Dagger / Hilt

**Website:** https://dagger.dev/hilt/  
**Source:** https://github.com/google/dagger  
**License:** Apache License, Version 2.0

Copyright 2012 The Dagger Authors.

Licensed under the Apache License, Version 2.0.

---

## Kotlin Standard Library and Coroutines

**Website:** https://kotlinlang.org  
**Source:** https://github.com/JetBrains/kotlin  
**License:** Apache License, Version 2.0

Copyright 2010–2024 JetBrains s.r.o. and Kotlin Programming Language
contributors.

Licensed under the Apache License, Version 2.0.

---

## MockK (test dependency only)

**Website:** https://mockk.io  
**Source:** https://github.com/mockk/mockk  
**License:** Apache License, Version 2.0

---

## Turbine (test dependency only)

**Website:** https://github.com/cashapp/turbine  
**License:** Apache License, Version 2.0

Copyright 2018 Square, Inc.

---

## Apache License, Version 2.0 (full text)

    Apache License
    Version 2.0, January 2004
    http://www.apache.org/licenses/

    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

    1. Definitions.
    ...

The full text is available at: https://www.apache.org/licenses/LICENSE-2.0

---

*This file was generated as part of the DownFlow project. If you believe
any attribution is missing or incorrect, please open an issue.*
