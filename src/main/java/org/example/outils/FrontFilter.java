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

        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setContentType("text/plain;charset=UTF-8");

        // Résoudre l'URL via UrlDispatcher et afficher Controller#method ou message d'absence
        String result = UrlDispatcher.handleRequest(servletPath, servletContext);
        try (PrintWriter printWriter = response.getWriter()) {
            printWriter.print(result);
        }
    }
}