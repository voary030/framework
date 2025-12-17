package org.example;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.example.outils.*;

public class FrontServlet extends HttpServlet {
    private RequestDispatcher defaultDispatcher;
    private RequestDispatcher jspDispatcher;
    private Map<String, MethodInfo> urlMappings = new java.util.HashMap<>();

    @Override
    public void init() throws ServletException {
        super.init();
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        jspDispatcher = getServletContext().getNamedDispatcher("jsp");
        
        try {
            // Utiliser les mappings du StartupListener au lieu de rescanner
            @SuppressWarnings("unchecked")
            Map<String, MethodInfo> contextMappings = (Map<String, MethodInfo>) getServletContext()
                .getAttribute(StartupListener.URL_MAPPINGS_KEY);
            
            if (contextMappings != null && !contextMappings.isEmpty()) {
                urlMappings = contextMappings;
                System.out.println("✅ [FrontServlet] Utilisation des mappings du StartupListener");
            } else {
                // Fallback si StartupListener n'a pas fonctionné
                System.out.println("⚠️  [FrontServlet] StartupListener mappings introuvables, scan local...");
                urlMappings = ClasspathScanner.scan("org.example.test");
            }
            
            if (urlMappings == null) {
                urlMappings = new java.util.HashMap<>();
            }
            
            getServletContext().setAttribute("controllerMappings", urlMappings);

            // debug : lister mappings
            System.out.println("📋 [FrontServlet] URLs mappées (" + urlMappings.size() + " routes):");
            for (Map.Entry<String, MethodInfo> entry : urlMappings.entrySet()) {
                System.out.println("   ├─ " + entry.getKey() + " -> " +
                        entry.getValue().getControllerClass().getName() + "#" + entry.getValue().getMethod().getName());
            }
        } catch (Exception ex) {
            urlMappings = new java.util.HashMap<>();
            System.err.println("❌ [FrontServlet] ControllerScanner init error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length()).toLowerCase();
        String httpMethod = req.getMethod().toUpperCase();  // GET, POST, PUT, DELETE, etc.

        // Vérifier si la ressource existe
        boolean ressources = getServletContext().getResource(path) != null;

        // Si c'est la racine, forward vers la page d'accueil (index.jsp)
        if ("/".equals(path)) {
            RequestDispatcher rd = req.getRequestDispatcher("/index.jsp");
            if (rd != null) {
                rd.forward(req, res);
                return;
            }
            // fallback minimal si le dispatcher est introuvable
            res.setContentType("text/html");
            try (PrintWriter out = res.getWriter()) {
                out.println("<html><body>");
                out.println("<h1>Path: /</h1>");
                out.println("</body></html>");
            }
            return;
        } else if (ressources) {
            // Si c'est une ressource statique existante, prioriser la servlet JSP si c'est une JSP
            try {
                if (path.endsWith(".jsp") && jspDispatcher != null) {
                    jspDispatcher.forward(req, res);
                    return;
                }
            } catch (Exception ignored) { }

            // Sinon servir la ressource via le dispatcher par défaut
            if (defaultDispatcher != null) {
                defaultDispatcher.forward(req, res);
                return;
            }
        }

        // Sprint 7: Déléguer à UrlDispatcher avec la méthode HTTP
        Object result = UrlDispatcher.handleRequestWithMethod(path, getServletContext(), req, httpMethod);

        if (result instanceof ModelView) {
            handleModelView(req, res, (ModelView) result);
            return;
        }

        // Sinon, afficher le résultat textuel ou message d'absence
        try (PrintWriter out = res.getWriter()) {
            res.setContentType("text/plain;charset=UTF-8");
            if (result == null) {
                out.println("Aucune correspondance trouvée pour: " + httpMethod + " " + path);
            } else {
                out.println(result.toString());
            }
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        res.setContentType("text/html");
        try (PrintWriter out = res.getWriter()) {
            out.println("<html><body>");
            out.println("<h1>Path: " + path + "</h1>");
            out.println("</body></html>");
        }
    }

    private boolean handleMappedMethod(HttpServletRequest req, HttpServletResponse res, Method m) throws IOException {
        Class<?> cls = m.getDeclaringClass();

        try {
            Object target = Modifier.isStatic(m.getModifiers()) ? null : cls.getDeclaredConstructor().newInstance();
            m.setAccessible(true);

            Class<?>[] params = m.getParameterTypes();
            Object result = null;

            if (params.length == 0) {
                result = m.invoke(target);
            } else if (params.length == 1 && HttpServletRequest.class.isAssignableFrom(params[0])) {
                result = m.invoke(target, req);
            } else if (params.length == 2
                    && HttpServletRequest.class.isAssignableFrom(params[0])
                    && HttpServletResponse.class.isAssignableFrom(params[1])) {
                result = m.invoke(target, req, res);
            } else {
                try (PrintWriter out = res.getWriter()) {
                    res.setContentType("text/plain;charset=UTF-8");
                    out.println("Unsupported method signature for invocation");
                }
                return true;
            }

            // si ModelView -> forward directement
            if (result instanceof ModelView) {
                handleModelView(req, res, (ModelView) result);
                return true;
            }

            // sinon on écrit du texte
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                if (!cls.isAnnotationPresent(org.example.annotation.Controller.class)) {
                    out.printf("classe non annote controller : %s%n", cls.getName());
                } else {
                    out.printf("Classe associe : %s%n", cls.getName());
                    out.printf("Nom de la methode: %s%n", m.getName());
                    handleReturnValue(out, m, result);
                }
            }

        } catch (InvocationTargetException ite) {
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                out.println("Erreur invocation: " + ite.getTargetException());
            }
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                out.println("Erreur invocation: " + ex.toString());
            }
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return true;
    }

    private void handleReturnValue(PrintWriter out, Method m, Object result) {
        Class<?> returnType = m.getReturnType();
        if (returnType == String.class) {
            if (result != null) {
                out.println(result.toString());
            } else {
                out.println("Retour null");
            }
        } else {
            out.println("le type de retour n'est pas string");
        }
    }

    private void handleModelView(HttpServletRequest req, HttpServletResponse res, ModelView mv)
            throws ServletException, IOException {
        if (mv == null) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("ModelView est null");
            return;
        }

        String view = mv.getView();
        if (view == null || view.isEmpty()) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("ModelView.view est null ou vide");
            return;
        }

        // Ajout des données du ModelView dans la requête
        if (mv.getData() != null) {
            for (String key : mv.getData().keySet()) {
                req.setAttribute(key, mv.getData().get(key));
            }
        }

        RequestDispatcher rd = req.getRequestDispatcher(view);
        if (rd == null) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Impossible d'obtenir RequestDispatcher pour la vue: " + view);
            return;
        }

        rd.forward(req, res);
    }
}
