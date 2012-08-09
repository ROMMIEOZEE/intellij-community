package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds a method foo to class X if X.foo() is unresolved. 
 * User: dcheryasov
 * Date: Apr 5, 2009 6:51:26 PM
 */
public class AddMethodQuickFix implements LocalQuickFix {

  private PyClassType myQualifierType;
  private String myIdentifier;

  public AddMethodQuickFix(String identifier, PyClassType qualifierType) {
    myIdentifier = identifier;
    myQualifierType = qualifierType;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", myIdentifier, myQualifierType.getName());
  }

  @NotNull
  public String getFamilyName() {
    return "Add method to class";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      // descriptor points to the unresolved identifier
      // there can be no name clash, else the name would have resolved, and it hasn't.
      PsiElement problem_elt = descriptor.getPsiElement().getParent(); // id -> ref expr
      PyClass cls = myQualifierType.getPyClass();
      boolean call_by_class = myQualifierType.isDefinition();
      String item_name = myIdentifier;
      sure(cls); sure(item_name);
      PyStatementList cls_stmt_list = cls.getStatementList();
      sure(CodeInsightUtilBase.preparePsiElementForWrite(cls_stmt_list));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(item_name);
      PsiElement pe = problem_elt.getParent();
      String deco_name = null; // set to non-null to add a decorator
      sure(pe instanceof PyCallExpression);
      PyArgumentList arglist = ((PyCallExpression)pe).getArgumentList();
      sure(arglist);
      final PyExpression[] args = arglist.getArguments();
      boolean made_instance = false;
      if (call_by_class) {
        if (args.length > 0) {
          PyType first_arg_type = args[0].getType(TypeEvalContext.fast());
          if (first_arg_type instanceof PyClassType && ((PyClassType)first_arg_type).getPyClass().isSubclass(cls)) {
            // class, first arg ok: instance method
            builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
            made_instance = true;
          }
        }
        if (! made_instance) { // class, first arg absent or of different type: classmethod
          builder.parameter("cls"); // NOTE: might use a name other than 'cls', according to code style.
          deco_name = PyNames.CLASSMETHOD;
        }
      }
      else { // instance method
        builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
      }
      boolean skip_first = call_by_class && made_instance; // ClassFoo.meth(foo_instance)
      for (PyExpression arg : args) {
        if (skip_first) {
          skip_first = false;
          continue;
        }
        if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(self, bar_1)
          builder.parameter(((PyKeywordArgument)arg).getKeyword());
        }
        else if (arg instanceof PyReferenceExpression) {
          PyReferenceExpression refex = (PyReferenceExpression)arg;
          builder.parameter(refex.getReferencedName());
        }
        else { // use a boring name
          builder.parameter("param");
        }
      }
      PyFunction meth = builder.buildFunction(project, LanguageLevel.getDefault());
      if (deco_name != null) {
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyDecoratorList deco_list = generator.createFromText(LanguageLevel.getDefault(), PyDecoratorList.class, "@" + deco_name + "\ndef foo(): pass", new int[]{0, 0});
        meth.addBefore(deco_list, meth.getFirstChild()); // in the very beginning
      }
      
      final PsiElement first_stmt = cls_stmt_list.getFirstChild();
      if (first_stmt == cls_stmt_list.getLastChild() && first_stmt instanceof PyPassStatement) {
        // replace the lone 'pass'
        meth = (PyFunction) first_stmt.replace(meth);
      }
      else {
        // add ourselves to the bottom
        meth = (PyFunction) cls_stmt_list.add(meth);
      }
      showTemplateBuilder(meth);
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.method"), MessageType.ERROR);
    }
  }

  private static void showTemplateBuilder(PyFunction method) {
    method = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(method);

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(
      method.getParameterList().getParameters(),
      new ParamHelper.ParamVisitor() {
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );

    builder.replaceElement(method.getStatementList(), PyNames.PASS);

    builder.run();
  }
}
