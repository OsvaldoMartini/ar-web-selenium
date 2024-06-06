javac --module-source-path src -d out
jar --create --file=app.jar --main-class=com.example.app.Main -C out/com.example.app .
