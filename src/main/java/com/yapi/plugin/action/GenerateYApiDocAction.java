package com.yapi.plugin.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.yapi.plugin.client.YApiClient;
import com.yapi.plugin.config.YApiServerConfig;
import com.yapi.plugin.config.YApiSettings;
import com.yapi.plugin.parser.SpringControllerParser;
import com.yapi.plugin.parser.model.YApiInterfaceInfo;
import com.yapi.plugin.ui.ServerSelectDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GenerateYApiDocAction extends AnAction {

    private static final String NOTIFICATION_GROUP = "YApi Doc Generator";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // Must specify BGT for IntelliJ 2024.2+, otherwise action may not show
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Always visible — validation happens in actionPerformed
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiElement psiElement = getPsiElement(e);
        if (psiElement == null) {
            showNotification(project, "Please select a Controller class or method in a Java file.",
                    NotificationType.WARNING);
            return;
        }

        PsiClass psiClass = findContainingClass(psiElement);
        if (psiClass == null) {
            showNotification(project, "No class found. Please select a Controller class or method.",
                    NotificationType.WARNING);
            return;
        }

        // Flexible controller check
        if (!isControllerClass(psiClass)) {
            showNotification(project, "The selected class is not a Spring MVC Controller.",
                    NotificationType.WARNING);
            return;
        }

        // Determine if we're on a method or class
        PsiMethod psiMethod = findContainingMethod(psiElement);

        // Get settings
        YApiSettings settings = YApiSettings.getInstance(project);
        if (settings.servers.isEmpty()) {
            int result = Messages.showYesNoDialog(project,
                    "No YApi servers configured. Would you like to configure one now?",
                    "YApi Doc Generator", Messages.getQuestionIcon());
            if (result == Messages.YES) {
                ApplicationManager.getApplication().invokeLater(() ->
                        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                                .showSettingsDialog(project, "YApi Doc Generator"));
            }
            return;
        }

        // 按模块根目录名自动匹配 YApi 服务器配置
        YApiServerConfig selectedServer = findServerByModule(psiClass, settings.servers);
        if (selectedServer == null && settings.servers.size() == 1) {
            selectedServer = settings.servers.get(0);
        }
        if (selectedServer == null && settings.servers.size() > 1) {
            ServerSelectDialog dialog = new ServerSelectDialog(settings.servers);
            if (!dialog.showAndGet()) return;
            selectedServer = dialog.getSelectedServer();
        }
        if (selectedServer == null) return;

        // --- Parse PSI on EDT (MUST be on read thread) ---
        SpringControllerParser parser = new SpringControllerParser(psiClass);
        List<YApiInterfaceInfo> interfaces;
        if (psiMethod != null) {
            YApiInterfaceInfo single = parser.parseMethod(psiMethod);
            interfaces = single != null ? List.of(single) : List.of();
        } else {
            interfaces = parser.parseClass();
        }

        if (interfaces.isEmpty()) {
            showNotification(project, "No REST API methods found in the selection.",
                    NotificationType.WARNING);
            return;
        }

        String categoryName = getCategoryName(psiClass);

        // --- Upload in background (no PSI access) ---
        YApiServerConfig finalServer = selectedServer;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating YApi Doc...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                YApiClient client = new YApiClient();

                try {
                    indicator.setFraction(0.1);
                    indicator.setText("Resolving category...");
                    String catId = client.resolveCategoryId(finalServer, categoryName);

                    int total = interfaces.size();
                    int successCount = 0;
                    int failCount = 0;
                    StringBuilder errors = new StringBuilder();

                    for (int i = 0; i < interfaces.size(); i++) {
                        YApiInterfaceInfo info = interfaces.get(i);
                        info.setCatId(catId);

                        indicator.setFraction(0.1 + 0.8 * (i + 1) / total);
                        indicator.setText("Uploading " + info.getMethod() + " " + info.getPath());

                        try {
                            String response = client.createOrUpdateInterface(finalServer, info);
                            if (response.contains("\"errcode\":0")) {
                                successCount++;
                            } else {
                                failCount++;
                                errors.append(info.getMethod()).append(" ").append(info.getPath())
                                        .append(": ").append(extractErrmsg(response)).append("\n");
                            }
                        } catch (Exception ex) {
                            failCount++;
                            errors.append(info.getMethod()).append(" ").append(info.getPath())
                                    .append(": ").append(ex.getMessage()).append("\n");
                        }
                    }

                    indicator.setFraction(1.0);
                    indicator.setText("Complete");

                    String notificationMsg;
                    NotificationType notificationType;
                    if (failCount == 0) {
                        notificationMsg = "Successfully generated " + successCount
                                + " API doc(s) to [" + finalServer.getName() + "]";
                        notificationType = NotificationType.INFORMATION;
                    } else {
                        notificationMsg = "Generated " + successCount + " doc(s), "
                                + failCount + " failed.\n\nErrors:\n" + errors;
                        notificationType = NotificationType.ERROR;
                    }
                    ApplicationManager.getApplication().invokeLater(() ->
                            showNotification(project, notificationMsg, notificationType));
                } catch (Exception ex) {
                    indicator.setFraction(1.0);
                    ApplicationManager.getApplication().invokeLater(() ->
                            showNotification(project, "Upload failed: " + ex.getMessage(),
                                    NotificationType.ERROR));
                }
            }
        });
    }

    private PsiElement getPsiElement(AnActionEvent e) {
        // 1. Try editor popup — use caret position
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (psiFile instanceof PsiJavaFile javaFile && editor != null) {
            int offset = editor.getCaretModel().getPrimaryCaret().getOffset();
            PsiElement element = javaFile.findElementAt(offset);
            if (element != null) return element;
        }

        // 2. Try PSI_ELEMENT from data context (works for both popup types)
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {
            return psiElement;
        }

        // 3. Try PSI_FILE from project view popup
        if (psiFile instanceof PsiJavaFile && editor == null) {
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            // Return the first class (or the one matching filename)
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) return classes[0];
        }

        return psiElement;
    }

    private PsiClass findContainingClass(PsiElement element) {
        while (element != null) {
            if (element instanceof PsiClass) {
                return (PsiClass) element;
            }
            element = element.getParent();
        }
        return null;
    }

    private PsiMethod findContainingMethod(PsiElement element) {
        while (element != null) {
            if (element instanceof PsiClass) return null; // reached class level
            if (element instanceof PsiMethod) return (PsiMethod) element;
            element = element.getParent();
        }
        return null;
    }

    /**
     * Flexible controller detection: tries qualified annotation name first,
     * then falls back to short name matching and class name suffix.
     */
    private boolean isControllerClass(PsiClass psiClass) {
        // Try the strict parser
        SpringControllerParser parser = new SpringControllerParser(psiClass);
        if (parser.isController()) return true;

        // Fallback: check annotation short names from PSI text
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
            PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation ann : annotations) {
                String text = ann.getQualifiedName();
                if (text == null) continue;
                if (text.endsWith("RestController") || text.endsWith("Controller")) {
                    return true;
                }
                // Also check the annotation text itself
                String annText = ann.getText();
                if (annText.contains("RestController") || annText.contains("Controller")) {
                    return true;
                }
            }
        }

        // Last resort: class name suffix
        String name = psiClass.getName();
        if (name != null && (name.endsWith("Controller") || name.endsWith("RestController"))) {
            return true;
        }

        return false;
    }

    /**
     * 根据 PSI 类所在模块的根目录名称，自动匹配对应的 YApi 服务器配置。
     * 匹配规则：服务器配置名称与模块根目录名忽略大小写和分隔符比较。
     * 例如：模块 "e-work" / "ework-backend-enterprise" 可匹配配置名 "e-work"。
     */
    @Nullable
    private YApiServerConfig findServerByModule(PsiClass psiClass, List<YApiServerConfig> servers) {
        if (servers.isEmpty()) return null;

        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile == null) return null;

        VirtualFile vf = psiFile.getVirtualFile();
        if (vf == null) return null;

        Module module = ModuleUtil.findModuleForFile(vf, psiClass.getProject());
        if (module == null) return null;

        // 取模块的根目录名（即模块的 content root 目录名）
        String moduleRootName = null;
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        if (contentRoots.length > 0) {
            moduleRootName = contentRoots[0].getName();
        }

        // 也尝试用模块名
        String moduleName = module.getName();

        // 对每个服务器配置名做模糊匹配
        for (YApiServerConfig server : servers) {
            if (server.getName() == null) continue;
            String serverName = server.getName().toLowerCase();

            if (moduleRootName != null) {
                String rootName = moduleRootName.toLowerCase()
                        .replaceAll("[-_]", "");
                String sName = serverName.replaceAll("[-_]", "");
                if (rootName.contains(sName) || sName.contains(rootName)) {
                    return server;
                }
            }

            // 回退：用 IDEA 模块名匹配（不含文件扩展名的 .iml 名）
            if (moduleName != null) {
                String mName = moduleName.toLowerCase()
                        .replaceAll("[-_]", "");
                String sName = serverName.replaceAll("[-_]", "");
                if (mName.contains(sName) || sName.contains(mName)) {
                    return server;
                }
            }
        }

        // 如果完全匹配不到，尝试取设置为默认的服务器
        for (YApiServerConfig server : servers) {
            if (server.isDefault()) return server;
        }

        return null;
    }

    private String getCategoryName(PsiClass psiClass) {
        SpringControllerParser parser = new SpringControllerParser(psiClass);
        List<YApiInterfaceInfo> interfaces = parser.parseClass();
        if (!interfaces.isEmpty()) {
            String path = interfaces.get(0).getPath();
            // Use first path segment as category
            String[] parts = path.split("/");
            for (String part : parts) {
                if (!part.isEmpty()) return part;
            }
        }
        String name = psiClass.getName();
        if (name != null && name.endsWith("Controller")) {
            name = name.substring(0, name.length() - "Controller".length());
        }
        return name != null ? name : "default";
    }

    private String extractErrmsg(String response) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response).getAsJsonObject();
            if (json.has("errmsg")) return json.get("errmsg").getAsString();
        } catch (Exception ignored) {
        }
        return response.length() > 100 ? response.substring(0, 100) + "..." : response;
    }

    private void showNotification(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(message, type)
                .notify(project);
    }
}
