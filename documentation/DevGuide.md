# Development Guide

This document describes how to setup the development enviroment for this mod.


### Dependencies
- [BankSystemMod](https://github.com/KROIA/BankSystem)
- [MC_ModUtilities](https://github.com/KROIA/MC_ModUtilities)

> [!NOTE]  
> All repositories need to be downloaded and placed in the correct folder structure in order to work

---
### Folder Structure
The Projects use relative paths. So the base for your mod sources can be chosen freely.
The folder structure below shows how i work with different minecraft versions, you just need to download 
the branches you work with.

```
mods
  +-- BankSystem
  |    +-- mc_1.20.1_BankSystem
  |    +-- mc_1.20.2_BankSystem
  |    +-- ... (different branches from the repository)
  |
  +-- StockMarket
  |    +-- MC_1.20.1_StockMarket
  |    +-- MC_1.20.2_StockMarket
  |    +-- ... (different branches from the repository)
  | 
  +-- MC_ModUtilities
       +-- mc_1.20.1_MC_ModUtilities
       +-- mc_1.20.2_MC_ModUtilities
       +-- ... (different branches from the repository)

```
Inside the folder `mc_1.20.1_BankSystem` is the repository for the BankSystem for mc 1.20.1.


---
## To download
- [IntelliJ](https://www.jetbrains.com/de-de/idea/download)
- [Java JDK](https://www.oracle.com/java/technologies/javase/jdk19-archive-downloads.html) (v19.0.2)
- [Java JRE](https://www.java.com/de/download/)

---
### Setup Intellij
Install the following plugins:
- Architectury


Open the Project: `mc_1.20.1_MC_ModUtilities`
1) Select the JDK by navigating to: `File->Project Structure->Project`
    SDK: `19.0.2`
    Language Level: `SDK default`

2) Navigating to: `File->Settings->Build, Execution, Deployment->Build Tools->Gradle`
    Select Gradle JVM: `19.0.2`

Run gradle by clicking on the elephant on the right side of the IDE and then the button with the two circular arows.
You may have to do these steps for all 3 mod projects when opening them the first time.

After gradle has finished, navigate inside the gradle tab to: `Tasks->build` and double click on to `build`.
After the build has completed, you can try to run Minecraft.
Navigate inside the gradle tab to: `forge->Tasks->loom` and double click on `runClient`.


Now open the `mc_1.20.1_BankSystem` mod, you can close the ModUtilities mod project.
1) Again change the Settings ad described above.
2) Run gradle
3) Build

Now open the `mc_1.20.1_StockMarket` mod, you can close the BankSystem mod project.
1) Again change the Settings ad described above.
2) Run gradle
3) Build

Before you can run Minecraft on the `mc_1.20.1_StockMarket` project, we need to copy the `mc_1.20.1_BankSystem` mod jar to the mods folder:
1) Navigate to: `mods\BankSystem\mc_1.20.1_BankSystem\forge\build\libs`
   `banksystem-forge-1.5.0_ALPHA.jar` Is used as the mod jar when you run Minecraft standalone.
   `banksystem-forge-1.5.0_ALPHA-dev-shadow.jar` Is used as the mod jar when you run Minecraft from inside IntelliJ.
2) Copy `banksystem-forge-1.5.0_ALPHA-dev-shadow.jar` to `mods\StockMarket\MC_1.20.1_StockMarket\forge\run\mods`.
3) Now you can run Minecraft for forge. If you use fabric, do the same for the fabric run folder: `MC_1.20.1_StockMarket\fabric\run\mods` using the fabrics mod jar.

---
### Apply changes from the dependency mods
When changes are made in the `mc_1.20.1_MC_ModUtilities` and you want to apply the changes to `mc_1.20.1_BankSystem` or `mc_1.20.1_StockMarket`, you need to load the `mc_1.20.1_MC_ModUtilities` again.
I don't know how to do this right but i do it like that:
1) Inside the `mc_1.20.1_MC_ModUtilities` project, make sure to do a build for all platforms. (forge, fabric, ...)
2) Inside the `mc_1.20.1_BankSystem` project, delete the folder `.gradle` in the projects root directory.
3) After deleting the folder, run gradle again.

If you know how to do that the right way, feel free to contact me. ^^