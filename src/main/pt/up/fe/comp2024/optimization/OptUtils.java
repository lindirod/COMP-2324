package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {


    public static String getCode(Symbol symbol) {
        return symbol.getName() + toOllirType(symbol.getType());
    }


    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp2() {

        return getCurrentTemp("tmp");
    }



    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static String getCurrentTemp(String prefix) {

        return prefix + getCurrentTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static int getCurrentTempNum() {

        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        String typeName = typeNode.get("name");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {

        StringBuilder code = new StringBuilder(".");

        if (type == null) {
            code.append("V");
            return code.toString();
        }

        if (type.isArray())
            code.append("array.");
        String tipo = type.getName();
        switch (tipo) {
            case "int":
                code.append("i32");
                break;
            case "boolean":
                code.append("bool");
                break;
            case "void":
                code.append("V");
                break;
            default:
                code.append(tipo);
                break;
        }
        return code.toString();

    }

    public static String toOllirType2(String typeName) {
        return toOllirType(typeName);
    }

    private static String toOllirType(String typeName) {
        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" ->  "bool";
            default -> "V";
        };

        return type;
    }





}
