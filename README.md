# jsse-url-tester

A simple console application to test HTTPS URLs over specific JVM + Apache HTTPClient.

It should test your combination of JVM/JSSE + Apache HTTPClient support over a user-specified HTTPS URL.
It demonstrates the issue around this exception, when a certificate chain cannot be verified.

Exception:
```java
sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```
