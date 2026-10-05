# Mario custom combat V5 runtime test

1. Pull `main`.
2. Double-click root `Native Builder.bat` and click `BUILD + TEST MARIO`; require `MARIO BUILD + TEST SUCCESS`.
3. Launch Matrix3, equip the same supported 1H sword, enter Mario mode with Ctrl+M, then press F once.
4. The right arm must visibly swing from the shoulder/body pivot. Reject the build if Mario remains mostly static and only the wrist/weapon flicks.
5. The sword must stay attached through wind-up, cut and recovery; Mario should remain upright with no V3-style body fold.
6. Hold F: the slash must not continuously restart.

Runtime acceptance is pending.
