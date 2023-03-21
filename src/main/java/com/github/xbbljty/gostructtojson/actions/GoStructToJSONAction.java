package com.github.xbbljty.gostructtojson.actions;

import com.alibaba.fastjson.JSON;
import com.goide.psi.GoFieldDeclaration;
import com.goide.psi.GoType;
import com.goide.psi.impl.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import javax.activation.UnsupportedDataTypeException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class GoStructToJSONAction extends AnAction {
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
            Messages.showErrorDialog("Not a struct", "GoStructToJSON");
            return;
        }

        var rootElement = (GoStructTypeImpl) (element.getParent());
        var fields = rootElement.getFieldDeclarationList();
        var out = new HashMap<>();
        try {
            arrangeFields(navPsiElement, fields, out);
        } catch (UnsupportedDataTypeException ex) {
            Messages.showErrorDialog("Unsupported type", "GoStructToJSON");
            return;
        }
        var jsonStr = JSON.toJSONString(out, true);
        System.out.println(jsonStr);
    }

    private void arrangeFields(PsiElement navPsiElement, List<GoFieldDeclaration> fields, HashMap<Object, Object> out) throws UnsupportedDataTypeException {
        for (var field : fields) {
            var key = getFieldName(field);
            Object value = arrangeType(navPsiElement, field.getType());
            out.put(key, value);
        }
    }

    private Object arrangeType(PsiElement navPsiElement, @Nullable GoType tp) throws UnsupportedDataTypeException {
        if (tp instanceof GoStructTypeImpl) {
            var out = new HashMap<>();
            var fields = ((GoStructTypeImpl) tp).getFieldDeclarationList();
            arrangeFields(navPsiElement, fields, out);
        } else if (tp instanceof GoArrayOrSliceTypeImpl) {
            var eleTp = GoTypeUtil.getArrayOrSliceElementType(tp);
            if (eleTp == null) {
                return null;
            }
            return arrangeType(navPsiElement, eleTp);
        } else if (tp instanceof GoMapTypeImpl) {
            var kt = ((GoMapTypeImpl) tp).getKeyType();
            if (!GoTypeUtil.isBasicType(kt, navPsiElement)) {
                throw new UnsupportedDataTypeException();
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
            if (GoTypeUtil.isNumericType(tp, navPsiElement)) {
                return 0;
            } else if (GoTypeUtil.isString(tp, navPsiElement)) {
                return "";
            }
            return null;
        }
        return null;
    }

    private String getFieldName(GoFieldDeclaration field) {
        var tag = field.getTag();
        if (tag != null && !Objects.equals(tag.getValue("json"), "")) {
            var tagStr = tag.getValue("json");
            if (tagStr != null && !tagStr.equals("")) {
                return tagStr.split(",")[0];
            }
        }
        return field.getText().split(" ")[0];
    }
}
