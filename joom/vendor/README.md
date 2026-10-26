# Existing remote-user authentication extension

`shiro-remote-user-0.0.1-SNAPSHOT.jar` is the 2017 Yahoo Apache-2.0 extension
already shipped by Joom in analytics/joom/deploy/docker-gcp/zeppelin.
Its embedded Maven POM records the original coordinates and copyright.
No authentication logic is changed. The adjacent `shiro-jakarta` build relocates
only `javax.servlet` references to match Zeppelin 0.12.1 / Jetty 11 / Shiro Jakarta.

SHA-256: `e60ad5760d291c615499629c74a52d17a485eba9f5e27d05c83e7045afe2b316`.

The root Apache LICENSE applies to this Apache-2.0 dependency as well.
