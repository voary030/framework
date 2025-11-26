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
        String servletPath = request.getServletPath();
        ServletContext servletContext = request.getServletContext();

        // Extraire l'URL sans le préfixe /api
        // /api/etudiant/liste → /etudiant/liste
        String routePath = servletPath;
        if (servletPath.startsWith("/api/")) {
            routePath = servletPath.substring(4); // Enlever "/api"
        }

        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setContentType("text/plain;charset=UTF-8");

        // Résoudre l'URL via UrlDispatcher et afficher Controller#method ou message d'absence
        Object result = UrlDispatcher.handleRequest(routePath, servletContext);
        try (PrintWriter printWriter = response.getWriter()) {
            if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;
                printWriter.print("View: " + mv.getView() + "\n");
                printWriter.print("Model: " + mv.getModel());
            } else {
                printWriter.print(result);
            }
        }
    }
}