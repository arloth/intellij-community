// "Mark 'alias' as safe" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      String s = foo();
      String alias = s;
      sink(<caret>alias);
    }

    String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}