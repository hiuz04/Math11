package com.mathio.lms.control;

import com.mathio.lms.DAO.CatalogDAO;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.QuestionDAO;
import com.mathio.lms.DAO.ReportingDAO;
import com.mathio.lms.DAO.UserDAO;
import com.mathio.lms.model.AiService;
import com.mathio.lms.model.AuthService;
import com.mathio.lms.model.ConfigurationService;
import com.mathio.lms.model.Environment;
import com.mathio.lms.model.EssayService;
import com.mathio.lms.model.ExamService;
import com.mathio.lms.model.GeminiClient;
import com.mathio.lms.model.ImageStorage;
import com.mathio.lms.model.TutorService;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class Application implements ServletContextListener {
    public Database database;
    public AuthService authService;
    public UserDAO userDAO;
    public CatalogDAO catalogDAO;
    public QuestionDAO questionDAO;
    public ExamService examService;
    public ConfigurationService configurationService;
    public AiService aiService;
    public EssayService essayService;
    public TutorService tutorService;
    public ReportingDAO reportingDAO;
    private GeminiClient geminiClient;

    public Application() {}

    public Application(Database database) {
        initialize(database);
    }

    private void initialize(Database database) {
        this.database = database;
        authService = new AuthService(database);
        userDAO = new UserDAO(database);
        catalogDAO = new CatalogDAO(database);
        questionDAO = new QuestionDAO(database);
        examService = new ExamService(database, questionDAO);
        configurationService = new ConfigurationService(database);
        geminiClient = new GeminiClient();
        aiService = new AiService(database, configurationService, geminiClient);
        essayService = new EssayService(database, questionDAO, aiService, new ImageStorage());
        tutorService = new TutorService(database, questionDAO, aiService, essayService);
        reportingDAO = new ReportingDAO(database);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String configFile =
                Environment.get(
                        "MATHIO_ENV_FILE",
                        event.getServletContext().getInitParameter("mathio.envFile"));
        if (configFile != null && !configFile.isBlank())
            Environment.load(java.nio.file.Path.of(configFile));
        initialize(Database.configured());
        event.getServletContext().setAttribute(Application.class.getName(), this);
        event.getServletContext().getSessionCookieConfig().setHttpOnly(true);
        event.getServletContext().getSessionCookieConfig().setSecure(Environment.production());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        try {
            if (database != null) database.close();
        } finally {
            if (geminiClient != null) geminiClient.close();
            event.getServletContext().removeAttribute(Application.class.getName());
            var drivers = java.sql.DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                var driver = drivers.nextElement();
                if (driver.getClass().getClassLoader() == Application.class.getClassLoader()) {
                    try {
                        java.sql.DriverManager.deregisterDriver(driver);
                    } catch (java.sql.SQLException e) {
                        event.getServletContext()
                                .log("Cannot deregister application JDBC driver", e);
                    }
                }
            }
        }
    }

    public static Application get(ServletContext connection) {
        return (Application) connection.getAttribute(Application.class.getName());
    }
}
