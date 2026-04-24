@ECHO OFF
SETLOCAL

FOR %%I IN ("%~dp0.") DO SET "MVNW_DIR=%%~fI"
SET "WRAPPER_JAR=%MVNW_DIR%\.mvn\wrapper\maven-wrapper.jar"
SET "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"
SET "MVNW_USER_HOME=%MVNW_DIR%\.mvn\maven-user-home"

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Maven wrapper jar not found: "%WRAPPER_JAR%"
  EXIT /B 1
)

IF DEFINED JAVA_HOME (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_EXE=java"
)

"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%MVNW_DIR%" "-Dmaven.user.home=%MVNW_USER_HOME%" -classpath "%WRAPPER_JAR%" %MAVEN_OPTS% %JAVA_OPTS% %WRAPPER_LAUNCHER% %*
