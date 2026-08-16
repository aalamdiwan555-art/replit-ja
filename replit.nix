{pkgs}: {
  deps = [
    pkgs.android-tools
    pkgs.sdkmanager
    pkgs.gradle
    pkgs.jdk17_headless
  ];
}
