package com.github.xbbljty.gostructtojson.actions;

import com.goide.psi.GoFieldDeclaration;
import com.goide.psi.GoType;
import com.goide.psi.impl.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class GoStructToJSONAction extends AnAction {
    static String MessageBoxTitle = "GoStructToJson";

    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) {
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement navPsiElement = psiFile.getNavigationElement();
        if (navPsiElement == null) {
            return;
        }

        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) {
            return;
        }

        if (!(element.getParent() instanceof GoStructTypeImpl)) {
            Messages.showErrorDialog("Not a struct", MessageBoxTitle);
            return;
        }

        var rootElement = (GoStructTypeImpl) (element.getParent());
        var fields = rootElement.getFieldDeclarationList();
        var out = new HashMap<>();
        try {
            arrangeFields(navPsiElement, fields, out);
        } catch (Exception ex) {
            Messages.showErrorDialog("Unsupported type", MessageBoxTitle);
            return;
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        var jsonStr = gson.toJson(out);
        Messages.showInfoMessage(jsonStr + "\nThe results have been copied, please paste from clipboard(Ctrl+V).", MessageBoxTitle);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(jsonStr), null);
    }

    private void arrangeFields(PsiElement navPsiElement, List<GoFieldDeclaration> fields, HashMap<Object, Object> out) throws Exception {
        for (var field : fields) {
            var key = getFieldName(field);
            if (key.equals("-")) {
                continue;
            }
            Object value = arrangeType(navPsiElement, field.getType());
            out.put(key, value);
        }
    }


    private Object arrangeType(PsiElement navPsiElement, @Nullable GoType tp) throws Exception {
        if (tp instanceof GoStructTypeImpl) {
            var out = new HashMap<>();
            var fields = ((GoStructTypeImpl) tp).getFieldDeclarationList();
            arrangeFields(navPsiElement, fields, out);
        } else if (tp instanceof GoArrayOrSliceTypeImpl) {
            var eleTp = GoTypeUtil.getArrayOrSliceElementType(tp);
            if (eleTp == null) {
                return null;
            }
            if (Objects.equals(eleTp.getText(), "byte")) {
                return "str_val";
            } else {
                var list = new ArrayList<>();
                list.add(arrangeType(navPsiElement, eleTp));
                return list;
            }
        } else if (tp instanceof GoMapTypeImpl) {
            var kt = ((GoMapTypeImpl) tp).getKeyType();
            if (!GoTypeUtil.isIntegerType(kt, navPsiElement) && !GoTypeUtil.isString(kt, navPsiElement)) {
                throw new Exception();
            }
            var vt = ((GoMapTypeImpl) tp).getValueType();
            var out = new HashMap<>();
            out.put(arrangeType(navPsiElement, kt), arrangeType(navPsiElement, vt));
            return out;
        } else if (tp instanceof GoPointerTypeImpl) {
            return arrangeType(navPsiElement, ((GoPointerTypeImpl) tp).getType());
        } else if (tp instanceof GoInterfaceTypeImpl) {
            return null;
        } else {
            tp = Objects.requireNonNull(tp).getUnderlyingType(navPsiElement);
            if (GoTypeUtil.isNumericType(tp, navPsiElement)) {
                return 0;
            } else if (GoTypeUtil.isString(tp, navPsiElement)) {
                return "str_val";
            }
            return null;
        }
        return null;
    }

    private String getFieldName(GoFieldDeclaration field) {
        var ret = "-";
        var fieldName = field.getText().split(" ")[0];
        char c = fieldName.charAt(0);
        if (!Character.isUpperCase(c)) {
            return ret;
        }

        var tag = field.getTag();
        if (tag != null) {
            var jsonTagValue = tag.getValue("json");
            if (jsonTagValue != null) {
                var realTag = jsonTagValue.split(",")[0];
                if (!realTag.equals("") && !realTag.equals("-")) {
                    ret = realTag;
                }
            }
        } else {
            ret = fieldName;
        }
        return ret;
    }
}