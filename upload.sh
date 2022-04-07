cd ./third-party/aosp-dexutils
../../gradlew uploadArchives
cd ../bsdiff-util
../../gradlew uploadArchives
cd ../tinker-ziputils
../../gradlew uploadArchives

cd ../../tinker-commons
../gradlew uploadArchives

cd ../tinker-android/tinker-android-anno-support
../../gradlew uploadArchives
cd ../tinker-android-anno
../../gradlew uploadArchives
cd ../tinker-android-loader
../../gradlew uploadArchives
cd ../tinker-android-loader-no-op
../../gradlew uploadArchives
cd ../tinker-android-lib
../../gradlew uploadArchives
cd ../tinker-android-lib-no-op
../../gradlew uploadArchives

cd ../../tinker-build/tinker-patch-lib
../../gradlew uploadArchives
cd ../tinker-patch-gradle-plugin
../../gradlew uploadArchives
