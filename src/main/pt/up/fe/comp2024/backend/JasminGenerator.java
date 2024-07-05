package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.OperandType.INT32;
import static pt.up.fe.comp2024.JavammLexer.NEW;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(CondBranchInstruction.class, this::generateBranch);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded

        String superclass = classUnit.getSuperClass() != null ?
                classUnit.getSuperClass().toString():
                "java/lang/Object";
        String superCode = ".super " + superclass;

        code.append(superCode).append(NL);


        String c;

        for (var field : ollirResult.getOllirClass().getFields()) {
            switch(field.getFieldType().getTypeOfElement()) {
                case INT32 -> c = "I";
                case OBJECTREF -> c = buggyGetObjImports(field);
                case BOOLEAN -> c = "Z";
                case VOID -> c = "V";
                case STRING -> c = "Ljava/lang/String;";
                default -> c = "";
            }

            String access;

            if (field.getFieldAccessModifier().name().equals("PUBLIC")) {
                access = "public";
            }
            else {
                access = "";
            }

            String field_restriction;
            if (field.isFinalField()) { field_restriction = " final"; }
            else if (field.isStaticField()) { field_restriction = " static"; } else { field_restriction = ""; }

            String field_code = ".field " + access + field_restriction + " " + field.getFieldName() + " " + c + NL;

            code.append(field_code);

        }

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial""" + " " + superclass + """
                /<init>()V
                    return
                .end method
                """;
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    //buggyGetObjImports, auxForPutandGet, returnTypeObj são funções feitas para os casos em que os tipos de fields, return types ... são OBJECTREF
    private String buggyGetObjImports(Field field) {
        var aux_string = field.getFieldType().toString();
        int indexPrimeiroPar = aux_string.indexOf('(');
        int indexSegundoPar = aux_string.lastIndexOf(')');
        String complexField = "";
        String aux = "";

        if (indexPrimeiroPar != -1 && indexSegundoPar != -1 && indexPrimeiroPar < indexSegundoPar) {
            aux = aux_string.substring(indexPrimeiroPar + 1, indexSegundoPar);
        } else {
            return "";
        }

        for (var complexImport : ollirResult.getOllirClass().getImports()) {
            if (complexImport.substring(complexImport.lastIndexOf(".") + 1 ).equals(aux)) {
                complexField = ("L" + complexImport).replace(".","/");
            }
        }

        return complexField;
    }

    private String auxForPutandGet(Operand field) {
        var aux_string = field.getType().toString();
        int indexPrimeiroPar = aux_string.indexOf('(');
        int indexSegundoPar = aux_string.lastIndexOf(')');
        String complexField = "";
        String aux = "";

        if (indexPrimeiroPar != -1 && indexSegundoPar != -1 && indexPrimeiroPar < indexSegundoPar) {
            aux = aux_string.substring(indexPrimeiroPar + 1, indexSegundoPar);
        } else {
            return "";
        }

        for (var complexImport : ollirResult.getOllirClass().getImports()) {
            if (complexImport.substring(complexImport.lastIndexOf(".") + 1 ).equals(aux)) {
                complexField = (complexImport).replace(".","/");
            }
        }

        return complexField;
    }

    private String returnTypeObj(Type returnType) {
        var returnTypeCode = "";
        var aux_string = returnType.toString();


        int indexPrimeiroPar = aux_string.indexOf('(');
        int indexSegundoPar = aux_string.lastIndexOf(')');

        String aux = "";

        if (indexPrimeiroPar != -1 && indexSegundoPar != -1 && indexPrimeiroPar < indexSegundoPar) {
            aux = aux_string.substring(indexPrimeiroPar + 1, indexSegundoPar);
        } else {
            return "";
        }

        for (var complexImport : ollirResult.getOllirClass().getImports()) {
            if (complexImport.substring(complexImport.lastIndexOf(".") + 1 ).equals(aux)) {
                returnTypeCode = (complexImport).replace(".","/");
            }
        }
        return ("L" + returnTypeCode);
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        String method_restriction;
        if (method.isFinalMethod()) { method_restriction = "final "; }
        else if (method.isStaticMethod()) { method_restriction = "static "; } else { method_restriction = ""; }

        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded
        code.append("\n.method ").append(modifier).append(method_restriction).append(methodName).append("(");

        //gets the parameters types
        for (Element argument : method.getParams()) {
            switch(argument.getType().getTypeOfElement()) {
                case INT32:
                    code.append("I");
                    break;
                case BOOLEAN:
                    code.append("Z");
                    break;
                case VOID:
                    code.append("V");
                    break;
                case STRING:
                    code.append("Ljava/lang/String;");
                    break;
                case ARRAYREF:
                    code.append(buggyGetArrayType(argument.getType().getTypeOfElement().toString()));
                    break;
                case OBJECTREF:
                    code.append(returnTypeObj(argument.getType()));
                    break;
                default:
                    break;
            }
        }

        code.append(")");

        var returnType = method.getReturnType().getTypeOfElement();
        var tipodoarray = method.getReturnType().toString();

        switch (returnType) {
            case INT32:
                code.append("I\n");
                break;
            case BOOLEAN:
                code.append("Z\n");
                break;
            case VOID:
                code.append("V\n");
                break;
            case STRING:
                code.append("Ljava/lang/String;\n");
                break;
            case ARRAYREF:
                code.append(buggyGetArrayType(tipodoarray));
                code.append("\n");
                break;
            case OBJECTREF:
                code.append(returnTypeObj(method.getReturnType()));
                code.append("\n");
                break;
            default:code.append("\n");
        }

        var nolabels = method.getLabels();
        //if (nolabels.size() > 0) {
        //    for (var branches : method.getLabels().values()) {
        //        code.append(branch_constructor(branches));
        //    }
        //}

        // hardcoded. Does not interfere with simple cases
        var varTableVars = new String(String.valueOf(method.getVarTable().keySet().size()));

        var varTableVars2 = (method.getVarTable().keySet().size()) * 2;

        var varTableVars2str = new String(String.valueOf(varTableVars2));

        // Add limits
        //code.append(TAB).append(".limit stack ").append(varTableVars).append(NL);
        //code.append(TAB).append(".limit locals ").append(varTableVars2str).append(NL);

        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);
        for (var inst : method.getInstructions()) {

            for (var branches : method.getLabels().values()) {
                if (inst == branches) {
                    var lista_de_labels = method.getLabels();
                    for (var lab : lista_de_labels.entrySet()) {
                        if (lab.getValue() == inst) {
                            var a=lista_de_labels.get(lab);
                            code.append(lab.getKey().toString()).append(":\n");
                        }
                    }
                }
            }
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            if ((inst.getInstType() == InstructionType.CALL)&&(((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID)) {
                code.append("pop\n");
            }

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }



    private String generateCall(CallInstruction callInstruction) {
        var code  = new StringBuilder();
        var operand = (Operand) callInstruction.getOperands().get(0);

        var invocationType = callInstruction.getInvocationType();

        switch (invocationType) {
            case arraylength -> code.append(lenghtOFarray(callInstruction)).append(("\n"));
            case NEW -> code.append(handleNewCalls(callInstruction)).append(("\n"));
            //case NEW -> code.append("new ").append(operand.getName()).append("\n");
            case invokespecial -> code.append(invokeSpecial(callInstruction));
            case invokevirtual -> code.append(invokeVirtual(callInstruction));
            case invokestatic -> code.append(invokeStatic(callInstruction));
        }

        return code.toString();
    }

    //dont know if array is the only exception
    private String handleNewCalls(CallInstruction neww) {
        var operand_name = (Operand) neww.getOperands().get(0);
        var aux_new_normal = operand_name.getName();

        var aux_new = neww.getReturnType().getTypeOfElement().toString();

        var aux_new_arrayref = neww.getReturnType().toString();

        if (aux_new.equals("ARRAYREF")) {
            var arraylength = neww.getOperands().get(1);
            var arrayrefType = "";

            //da o load necessario para o newarray, o count
            arrayrefType = arrayrefType + (generators.apply(arraylength)) + "\n";
            switch (aux_new_arrayref) {
                case "INT32[]" -> { arrayrefType = arrayrefType + "newarray int"; }
                case "BOOLEAN[]" -> { arrayrefType = arrayrefType + "newarray boolean"; } //DONT KNOW IF THIS IS RIGHT
                case "OBJECTREF[]" -> { arrayrefType= arrayrefType + returnTypeObj(neww.getReturnType()).substring(0, returnTypeObj(neww.getReturnType()).length() - 1); } //DONT KNOW IF THIS IS RIGHT
                case "STRING[]", "ARRAYREF" -> { arrayrefType = arrayrefType + "newarray java/lang/String"; } //DONT KNOW IF THIS IS RIGHT
            }
            return arrayrefType;
        }
        else return "new " + aux_new_normal;
    }
    private String lenghtOFarray(CallInstruction callInstruction) {
        var code = "";

        var debug = callInstruction.getOperands().get(0);


        //arrayref
        code= code + (generators.apply(callInstruction.getOperands().get(0)));

        //recebe arrayref e devolve o tamanho do array
        code = code + ("arraylength") + "\n";

        return code;
    }

    private String invokeSpecial(CallInstruction callInstruction) {
        var code = new StringBuilder();

        code.append(generators.apply(callInstruction.getOperands().get(0)));

        var debug = generators.apply(callInstruction.getOperands().get(0));
        var caller = (ClassType) callInstruction.getCaller().getType();

        var complexImportexist = "";

        for (var complexImport : ollirResult.getOllirClass().getImports()) {
            if (complexImport.substring(complexImport.lastIndexOf(".") + 1 ).equals(caller.getName())) {
                complexImportexist = complexImport;
            }
        }
        complexImportexist = complexImportexist.replace(".","/");

        if (complexImportexist != "") {
            code.append("invokespecial ").append(complexImportexist).append("/<init>");
        }
        else {
            code.append("invokespecial ").append(ollirResult.getOllirClass().getClassName()).append("/<init>");
        }

        //code.append("invokespecial ").append(ollirResult.getOllirClass().getClassName()).append("/<init>");


        code.append("(");

        for (Element el : callInstruction.getArguments()) {
            switch (el.getType().getTypeOfElement()) {
                case INT32 -> code.append("I\npop");
                case BOOLEAN -> code.append("Z\npop");
                case VOID -> code.append("V");
                case STRING -> code.append("Ljava/lang/String;\npop");
                case OBJECTREF -> code.append(returnTypeObj(el.getType()));
            }
        }

        code.append(")");

        var returnType = callInstruction.getReturnType().getTypeOfElement();

        switch (returnType) {
            case OBJECTREF -> code.append(returnTypeObj(callInstruction.getReturnType()));
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case VOID -> code.append("V");
            case STRING -> code.append("Ljava/lang/String;");
        }

        return code.append("\n").toString();
    }

    private String invokeVirtual(CallInstruction callInstruction) {

        var code = new StringBuilder();

        var caller = (ClassType) callInstruction.getCaller().getType();

        var debug = callInstruction.getOperands().get(0);

        code.append(generators.apply(callInstruction.getOperands().get(0)));

        for (Element virtualElement : callInstruction.getArguments())
            code.append(generators.apply(virtualElement));

        var complexImportexist = "";

        for (var complexImport : ollirResult.getOllirClass().getImports()) {
            if (complexImport.substring(complexImport.lastIndexOf(".") + 1 ).equals(caller.getName())) {
                complexImportexist = complexImport;
            }
        }
        complexImportexist = complexImportexist.replace(".","/");

        if (complexImportexist != "") {
            code.append("invokevirtual ").append(complexImportexist).append("/");
        }
        else {
            code.append("invokevirtual ").append(caller.getName()).append("/");
        }

        //code.append("invokevirtual ").append(caller.getName()).append("/");

        var literal = (LiteralElement) callInstruction.getOperands().get(1);

        code.append(literal.getLiteral().replace("\"", ""));

        code.append("(");

        for (Element el : callInstruction.getArguments()) {
            var debug3 = el.getType().getTypeOfElement();
            var debug2 = el.getType();
            switch (el.getType().getTypeOfElement()) {
                case INT32 -> code.append("I");
                case BOOLEAN -> code.append("Z");
                case VOID -> code.append("V");
                case STRING -> code.append("Ljava/lang/String;");
                case OBJECTREF -> code.append("[").append(returnTypeObj(el.getType()));
                case ARRAYREF -> code.append(buggyGetArrayType(el.getType().toString()));
            }
        }

        code.append(")");

        var returnType = callInstruction.getReturnType().getTypeOfElement();
        var returnTypeObj = callInstruction.getReturnType();

        switch (returnType) {
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case VOID -> code.append("V\npop");
            case OBJECTREF -> code.append(returnTypeObj(returnTypeObj));
            case STRING -> code.append("Ljava/lang/String;");
            case ARRAYREF -> code.append(buggyGetArrayType(returnTypeObj.toString()));
        }

        return code.append("\n").toString();
    }

    private String invokeStatic(CallInstruction callInstruction) {

        var code = new StringBuilder();

        var caller = (Operand) callInstruction.getOperands().get(0);

        for (Element staticElement : callInstruction.getArguments())
            code.append(generators.apply(staticElement));

        var complexImportexist = "";

        for (var complexImport : ollirResult.getOllirClass().getImports()) {
            if (complexImport.substring(complexImport.lastIndexOf(".") + 1 ).equals(caller.getName())) {
                complexImportexist = complexImport;
            }
        }

        complexImportexist = complexImportexist.replace(".", "/");

        if (complexImportexist != "") {
            code.append("invokestatic ").append(complexImportexist).append("/");
        }
        else {
            code.append("invokestatic ").append(caller.getName()).append("/");
        }
        //code.append("invokestatic ").append(caller.getName()).append("/");

        var literal = (LiteralElement) callInstruction.getOperands().get(1);

        code.append(literal.getLiteral().replace("\"", ""));

        code.append("(");

        for (Element el : callInstruction.getArguments()) {
            switch (el.getType().getTypeOfElement()) {
                case INT32 -> code.append("I");
                case BOOLEAN -> code.append("Z");
                case VOID -> code.append("V");
                case OBJECTREF -> code.append(returnTypeObj(el.getType()));
                case STRING -> code.append("Ljava/lang/String;");
            }
        }

        code.append(")");

        var returnType = callInstruction.getReturnType().getTypeOfElement();

        switch (returnType) {
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case VOID -> code.append("V");
            case OBJECTREF -> code.append(returnTypeObj(callInstruction.getReturnType()));
            case STRING -> code.append("Ljava/lang/String;");
        }

        return code.append("\n").toString();
    }


    private String generatePutField(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();

        var first_op = putFieldInstruction.getOperands().get(0);
        var callerType = (ClassType) first_op.getType();
        var field = (Operand) putFieldInstruction.getOperands().get(1);
        var third_op = putFieldInstruction.getOperands().get(2);
        var p = putFieldInstruction.getOperands().get(1);

        code.append(generators.apply(first_op)).append(generators.apply(third_op));

        code.append("putfield ").append(callerType.getName()).append("/").append(field.getName()).append(" ");

        switch (field.getType().getTypeOfElement()) {
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case OBJECTREF -> code.append("L").append(auxForPutandGet(field));
            case VOID -> code.append("V");
            case STRING -> code.append("Ljava/lang/String;");
        }

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        var first_op = getFieldInstruction.getOperands().get(0);
        var callerType = (ClassType) first_op.getType();
        var field = (Operand) getFieldInstruction.getOperands().get(1);

        code.append(generators.apply(first_op));

        code.append("getfield ").append(callerType.getName()).append("/").append(field.getName()).append(" ");

        switch (field.getType().getTypeOfElement()) {
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case VOID -> code.append("V");
            case OBJECTREF -> code.append("L").append(auxForPutandGet(field));
            case STRING -> code.append("Ljava/lang/String;");
        }

        return code.append("\n").toString();
    }

    private String generateBranch(CondBranchInstruction branchIn) {
        //exemplo SimpleControlFlow - Inst Branch -> Inst BINARYOPER -> Operand b.INT32 GTE -> Operand a.INT32 -> LABEL ELSE_0
        // a branch instruction é seguida de outro instruction de tipo BinaryOper ou UnaryOper ou NOPER??
        var branch = "";
        var secondInstruction = branchIn.getCondition().getInstType();

        for (var child : branchIn.getCondition().getChildren()) {
            if (child instanceof Operation) {
                var a = ((Operation) child).getOpType();
            }
        }

        if(secondInstruction.toString() == "BINARYOPER") {

            var tipo_de_comp = ((BinaryOpInstruction)(branchIn.getCondition())).getOperation().getOpType();

            var primeiro = ((BinaryOpInstruction)(branchIn.getCondition())).getLeftOperand();
            var segundo = ((BinaryOpInstruction)(branchIn.getCondition())).getRightOperand();

            branch = branch + (generators.apply(primeiro)) + (generators.apply(segundo));

            var next = branchIn.getLabel().toString();

            branch = branch + dealWithOperation(tipo_de_comp) + " " + next + "\n";

            return branch;
        }

        //else if (secondInstruction.toString() == "UNARYOPER") {
        else {
            //var debug = branchIn.getCondition();
            var unicop = ((SingleOpInstruction)(branchIn.getCondition())).getSingleOperand();

            var simple_branch = (generators.apply(unicop));

            var debug = branchIn.getLabel().toString();

            simple_branch = simple_branch + "ifeq " + branchIn.getLabel().toString() + "\n";

            return simple_branch;
        }

    }

    private String dealWithOperation(OperationType operationType) {
        return switch (operationType) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case ANDB -> "iand";
            case NOTB -> "ifeq";
            case LTH -> "if_icmplt";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            case GTH -> "if_icmpgt";
            default -> throw new NotImplementedException(operationType);
        };
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + "\n";
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var typeOfElement = operand.getType().getTypeOfElement();

        switch(typeOfElement) {
            case ARRAYREF,OBJECTREF,THIS -> code.append("astore ").append(reg).append(NL);
            case INT32, BOOLEAN -> code.append("istore ").append(reg).append(NL);
        }

        // TODO: Hardcoded for int type, needs to be expanded

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {

        var code = new StringBuilder();

        String type = literal.getType().toString();

        if (!type.equals("INT32") && !type.equals("BOOL")) {
            code.append("ldc ").append(literal.getLiteral()).append(NL);
        }
        else {
            int val = Integer.parseInt(literal.getLiteral());

            if (val >= 0 && val <= 5) {
                code.append("iconst_").append(val).append(NL);
            }
            else if (val >= -127 && val <= 127) {
                code.append("bipush ").append(val).append(NL);
            }
            else if (val >= -32767 && val <= 32767) {
                code.append("sipush ").append(val).append(NL);
            }
            else {
                code.append("ldc ").append(val).append(NL);
            }
        }

        return code.toString();
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        String code = "";

        var type = operand.getType().getTypeOfElement();

        switch (type) {
            case INT32, BOOLEAN -> code = "iload " + reg + NL;
            case STRING, OBJECTREF, ARRAYREF -> code = "aload " + reg + NL;
            case THIS -> code = "aload_0" + NL;
        }


        return code;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub ";
            case MUL -> "imul";
            case DIV -> "idiv ";
            case ANDB -> "iand";
            case ORB -> "ior";
            case NOTB -> "ifeq";
            case EQ -> "if_icmpeq";
            case LTH -> "if_icmplt";
            case GTE -> "if_icmpge";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded to int return type, needs to be expanded

        switch (returnInst.getElementType()) {
            case INT32,BOOLEAN -> {
                code.append(generators.apply(returnInst.getOperand()));
                code.append("\nireturn").append("\n");
            }
            case VOID -> code.append("\nreturn").append("\n");
            case STRING,ARRAYREF -> {
                code.append(generators.apply(returnInst.getOperand()));
                code.append("\nareturn").append("\n");
            }
            case OBJECTREF -> {
                var aux_string = generators.apply(returnInst.getOperand());
                code.append(aux_string);
                code.append("\nareturn").append("\n");
            }
        }
        return code.toString();
    }

    //não está muito bem feito
    private String buggyGetArrayType(String type) {
        switch (type) {
            case "INT32[]" -> { return "[I"; }
            case "BOOLEAN[]" -> { return "[Z"; }
            case "OBJECTREF[]" -> { return "[L"; }
            case "STRING[]" -> { return "Ljava/lang/String;"; }
            case "ARRAYREF" -> { return "[Ljava/lang/String;"; }
            default -> { return ""; }
        }
    }

}