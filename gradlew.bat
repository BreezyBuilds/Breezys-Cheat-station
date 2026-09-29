@echo off
rem Windows launcher: uses the wrapper jar if present, else a locally installed Gradle 8.x.
set APP_HOME=%~dp0
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  gradle -p "%APP_HOME%" %*
)
