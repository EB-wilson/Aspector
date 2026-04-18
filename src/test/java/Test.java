import aspector.Using;
import aspector.annotations.Stub;
import aspector.classes.ASMClassAccessor;
import aspector.classes.ClassDecl;
import aspector.classes.ClassName;

import java.awt.*;

public class Test extends @Stub Panel {
  public @TestAnno(
      str = "test text",
      type = Aspect.class,
      arrayTypes = { Aspect.class, AccessStub.class },
      arrayEnum = { Using.AFTER_RETURN, Using.OVERRIDE },
      intArray = { 12, 25, 74, 1 }
  ) String test(
      String a
  ){
    return a;
  }

  public static void main(String[] args) {
    ASMClassAccessor accessor = new ASMClassAccessor();
    ClassDecl<Test> decl = accessor.getClassDecl(ClassName.byClass(Test.class));

    decl.getMethods();
  }
}
