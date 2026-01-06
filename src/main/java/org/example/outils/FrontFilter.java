package org.example.outils;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

public class FrontFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        // Pour toute autre URL, chercher un contrôleur correspondant
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        // Si c'est une ressource statique (comme index.jsp), la servir normalement
        if (request.getServletPath().endsWith(".jsp")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        
        // SPRINT 9: Utiliser getRequestURI() comme FrontServlet, pas getServletPath() qui peut être vide
        String routePath = request.getRequestURI().substring(request.getContextPath().length()).toLowerCase();
        String httpMethod = request.getMethod().toUpperCase();
        ServletContext servletContext = request.getServletContext();

        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setContentType("text/plain;charset=UTF-8");

        // SPRINT 9: Utiliser handleRequestWithMethod pour supporter les méthodes HTTP ET JSON
        Object result = UrlDispatcher.handleRequestWithMethod(routePath, servletContext, request, httpMethod);
        try (PrintWriter printWriter = response.getWriter()) {
            if (result instanceof JsonResponse) {
                // SPRINT 9: Si c'est une JsonResponse, afficher en JSON
                JsonResponse json = (JsonResponse) result;
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(json.getCode());
                printWriter.print(json.toJson());
            } else if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;
                printWriter.print("View: " + mv.getView() + "\n");
                printWriter.print("Model: " + mv.getModel());
            } else {
                printWriter.print(result);
            }
        }
    }
}