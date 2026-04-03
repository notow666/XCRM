package cn.cordys.common.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author jianxing
 */
@Data
@Schema(description = "权限设置菜单项")
public class PermissionDefinitionItem implements Cloneable{
    @Schema(description = "菜单项ID")
    private String id;
    @Schema(description = "菜单项名称")
    private String name;
    @Schema(description = "是否是企业版菜单")
    private Boolean license = false;
    @Schema(description = "菜单是否全选")
    private Boolean enable = false;
    @Schema(description = "菜单下的权限列表")
    private List<Permission> permissions;
    @Schema(description = "菜单是否显示")
    private Boolean display = true;
    @Schema(description = "子菜单")
    private List<PermissionDefinitionItem> children;

    public static List<PermissionDefinitionItem> filterTree(List<PermissionDefinitionItem> nodes, Predicate<PermissionDefinitionItem> predicate) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        return nodes.stream()
                .filter(node -> predicate.test(node) || hasMatchInChildren(node, predicate))
                .map(node -> {
                    PermissionDefinitionItem filtered = node.clone();
                    if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                        filtered.setChildren(filterTree(node.getChildren(), predicate));
                    }
                    return filtered;
                })
                .collect(Collectors.toList());
    }

    /**
     * 检查子节点中是否有匹配项
     */
    private static boolean hasMatchInChildren(PermissionDefinitionItem node, Predicate<PermissionDefinitionItem> predicate) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return false;
        }
        return node.getChildren().stream()
                .anyMatch(child -> predicate.test(child) || hasMatchInChildren(child, predicate));
    }

    @Override
    public PermissionDefinitionItem clone() {
        try {
            PermissionDefinitionItem clone = (PermissionDefinitionItem) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
