# Java stdin/stdout round trip

`RunProcess.java` uses Nanoproc to launch `Echo.java` as a child JVM, sends UTF-8
input, checks the returned bytes, and prints the response. The child executable
comes from the current JDK, so no shell or external language installation is
needed. Both source files use Java's source-file launch mode.

From the repository root:

```sh
./mvnw verify
java --class-path 'target/*' examples/java/RunProcess.java
```

Expected output:

```text
Hello from nanoproc.
```

In Windows PowerShell replace `./mvnw` with `.\mvnw.cmd`. The classpath wildcard
is quoted so Java expands it to the packaged JARs in `target/`.

The example throws if execution fails or the output differs from the request.
`Echo.java` is a one-shot command that reads until EOF; it is not a persistent
framed worker.
