// "Annotate annotation 'Test' as @Retention" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Test {
}

class Main {
  private static boolean hasTestAnnotation(Method method) {
    return method.getAnnotation(Test.class) != null;
  }
}