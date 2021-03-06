## Times

The times below are milliseconds to compute a [recursively defined fibonacci(35)](code-examples.md).
Enfilade and Java times are under JDK 10, are after enough warmup runs (20,
though 10 might be enough) to get stable timings. Pharo/Cog doesn't seem to
benefit from warmup.

* 9200: R 3.4.2
* 6080: Javasacript - Firefox Quantum (58.0.2)
* 2440: Python 2.7.12
* 1450: Lua 5.2
* 1410: Trifle, profiling interpreter
* 1030: Ruby 2.3.1
* 860: Trifle, plain interpreter
* 380: Groovy 2.4, 3.0
* 145: Clojure 1.9.0
* 145: Lua JIT 2.0.4
* 129: Smalltalk - Pharo, 64-bit Cog, March05 2018 build
* 106: Javascript - node.js
* 82: Trifle, generic compiled form (wrapped ints)
* 66: gcc -O0
* 48: gcc -O1
* 40: Java
* 38: Trifle, adaptively specialized 
* 35: gcc -O2
* 21: gcc -O3

An implementation of fibonacci() where the recursive reference was available as a direct
field in the call had the times of 1140 for the profiling and 670 for the plain
interpreter.

There is a significant difference between JDK 9 and JDK 10 for some tests:

* Trifle, profiling interpreter
  * 9: 1860ms
  * 10: 1140ms 
* Trifle, adaptively specialized: no difference
* Java
  * 9: 37ms
  * 10: 40ms

Can't explain the difference between Java (fib() defined as a private static
method) and Trifle.
