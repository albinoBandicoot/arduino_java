This project is a compiler for a subset of Java targeting the Arduino, primarily for educational purposes.

Major language omissions include generics, nested classes, exceptions, and interfaces. Only a limited set of the Java standard libraries will be provided, along with an implementation of standard Arduino functions (e.g., millis(), noInterrupts(), Serial, etc.). 

The Arduino is too small to run a JVM, so native code will be produced directly.

This project has a long way to go. The assembler, lexer and parser are mostly done, as is the majority of semantic analysis. The intermediate representation(s) still need to be designed and generated; code generation needs to be done, as well as whatever optimizations I want to include. An editor and debugger might also be useful.
