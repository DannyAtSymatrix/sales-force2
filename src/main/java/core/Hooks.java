/*
 * © 2025 Daniel Ede. This code is licensed for personal or educational use only.
 * Unauthorized reproduction or distribution is prohibited.
 */

package core;

import io.cucumber.java.*;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import utils.context.ContextManager;
import utils.context.StoryContext;
import utils.context.ScenarioContext;
import common.pages.LoginPOM;
import io.cucumber.java.Status;
import listeners.StepLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Hooks {
    private static final Logger logger = Logger.getLogger(Hooks.class.getName());
    private static WebDriver driver;
    private static int stepCounter = 1;
    private static ExtentReports extentReports;
    private static ExtentTest extentTest;
    private static ScenarioContext scenarioContext = new ScenarioContext();
    private static String reportDir;
    public final static String reportNameFromConfig =  getReportPropertyFromConfig("report.name", "UnnamedProject");
    public final static String reportTitleFromConfig =  getReportPropertyFromConfig("report.title", "Extent");
    public static ExtentTest getExtentTest() {
        return extentTest;
    }

    @BeforeAll
    public static void setupReport() throws IOException {
        // ✅ Fix: Load config from context class loader to support child apps
        var configStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("logging.properties");

        if (configStream != null) {
            try {
                LogManager.getLogManager().readConfiguration(configStream);
                logger.info("✔️ Loaded logging.properties");
            } catch (IOException e) {
                System.err.println("❌ Failed to load logging config: " + e.getMessage());
            }
        } else {
            System.err.println("⚠️ logging.properties not found on classpath.");
        }

        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        String parentReportDir = "../test-reports"; // central location
        reportDir = parentReportDir + "/" + reportNameFromConfig + "_" + timestamp;

        Files.createDirectories(Path.of(reportDir));
        System.out.println("📁 Reports will be stored in: " + new File(reportDir).getAbsolutePath());
        logger.info("📁 Reports directory: " + new File(reportDir).getAbsolutePath());
        String reportPath = reportDir + "/"+ reportTitleFromConfig + "Report.html";
        

        ExtentSparkReporter sparkReporter = new ExtentSparkReporter(reportPath);
        sparkReporter.config().setDocumentTitle("Automation Test Report");
        sparkReporter.config().setReportName("Cucumber Selenium Test Execution");

        extentReports = new ExtentReports();
        extentReports.attachReporter(sparkReporter);
        logger.info("📊 Extent Reports initialized.");

    }

    @Before
    public void setup(Scenario scenario) {
    	try {
    		driver = core.WebDriverManager.getDriver();
    	} catch (Exception e) { 
    		System.out.println("Error setting driver");
    		e.printStackTrace();
    	}
        driver.manage().window().maximize();
        extentTest = extentReports.createTest(scenario.getName());

        // 🚀 Pass the scenarioContext to ContextManager so Story/Scenario modes can decide
        ContextManager.setScenarioContext(scenarioContext);

        if (StoryContext.isStoryMode()) {
            logger.info("📘 Story mode active — ignoring @Data_ tags on features.");
        } else {
            if (!ContextManager.containsKey("spreadsheet")) {
                extractSpreadsheetName(scenario);
            }
        }

        logger.info("✅ WebDriver Initialized");
    }

    @AfterStep
    public void takeScreenshotAfterStep(Scenario scenario) throws IOException {
        File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String scenarioName = scenario.getName().replaceAll(" ", "_");
        String screenshotDir = reportDir + "/screenshots";
        Files.createDirectories(Path.of(screenshotDir));

        String stepText = StepLogger.getCurrentStepText()
            .or(() -> Optional.ofNullable((String) scenarioContext.get("lastStepText")))
            .orElse("Step_" + stepCounter);
        System.out.println("👣 Step used in report: " + stepText);

        String cleanStep = stepText.replaceAll("[^a-zA-Z0-9-_]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss_SSS"));
        String screenshotName = scenarioName + "_" + cleanStep + "_" + timestamp + ".png";
        String screenshotPath = screenshotDir + "/" + screenshotName;

        try {
            Files.createDirectories(Path.of("target/screenshots"));
            Files.copy(srcFile.toPath(), Path.of(screenshotPath), StandardCopyOption.REPLACE_EXISTING);;
            logger.info("📸 Screenshot saved: " + screenshotPath);

            byte[] screenshot = Files.readAllBytes(srcFile.toPath());
            scenario.attach(screenshot, "image/png", stepText + " Screenshot");

            Status status = scenario.getStatus();
            if (scenario.isFailed()) {
                extentTest.fail("❌ '" + stepText + "' failed.").addScreenCaptureFromPath("screenshots/" + screenshotName);;
            } else if (status == Status.SKIPPED || status == Status.UNDEFINED) {
                extentTest.skip("⚠️ '" + stepText + "' was skipped or undefined.").addScreenCaptureFromPath("screenshots/" + screenshotName);;
            } else {
                extentTest.pass("✅ '" + stepText + "' passed.").addScreenCaptureFromPath("screenshots/" + screenshotName);;
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "❌ Failed to save screenshot: " + e.getMessage());
        }

        stepCounter++;
    }


    @After
    public void tearDown(Scenario scenario) throws IOException {
    	Status finalStatus = scenario.getStatus();

        if (finalStatus == Status.PASSED) {
            extentTest.pass("✅ Scenario passed successfully!");
        } else {
            String message;
            if (scenario.isFailed()) {
                message = "❌ Scenario failed due to an error.";
            } else if (finalStatus == Status.UNDEFINED || finalStatus == Status.SKIPPED) {
                message = "❌ Scenario failed due to skipped/undefined steps.";
            } else {
                message = "❌ Scenario ended with status: " + finalStatus;
            }

            String screenshotPath = captureScreenshot(scenario);
            if (screenshotPath != null) {
                extentTest.fail(message).addScreenCaptureFromPath(screenshotPath);
            } else {
                extentTest.fail(message);
            }
	    logoutIfNeeded();
        }

        try {
            core.WebDriverManager.quitDriver();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ Failed to quit browser: " + e.getMessage(), e);
        }
    }

    @AfterAll
    public static void flushReports() {
        extentReports.flush();
        logger.info("Extent Reports Generated: target/ExtentReport.html");

        scenarioContext = new ScenarioContext();
        logger.info("ScenarioContext cleared after feature execution.");
        
        utils.excel.ExcelFileManager.cleanOldResults("results", 6);
        logger.info("Tidied up results file");
        
        try {
            core.WebDriverManager.quitDriver();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ Failed to quit browser: " + e.getMessage(), e);
        }
     // Convert HTML to Word
        try {
            String htmlPath = reportDir + "/"+ reportTitleFromConfig + "Report.html";
            String screenshotsDir = reportDir;
            String outputDocx = reportDir + "/" + reportTitleFromConfig.replaceAll("[^a-zA-Z0-9]", "_") + "_Report.docx";

            utils.createworddocuments.CreateWordDocuments.generateWordFromHtml(htmlPath, screenshotsDir, outputDocx);
            logger.info("📄 Word document created at: " + outputDocx);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ Failed to generate Word document: " + e.getMessage(), e);
        }
        tidyDir();
    }

    private String captureScreenshot(Scenario scenario) throws IOException {
        File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String scenarioName = scenario.getName().replaceAll(" ", "_");
        String screenshotPath = reportDir + "/screenshots/" + scenarioName + "_Failed.png";
        Files.createDirectories(Path.of(reportDir + "/screenshots"));

        try {
            Files.createDirectories(Path.of("target/screenshots"));
            Files.copy(srcFile.toPath(), Path.of(screenshotPath));
            return screenshotPath;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "❌ Failed to capture failure screenshot: " + e.getMessage());
            return null;
        }
    }

    private void extractSpreadsheetName(Scenario scenario) {
        Optional<String> spreadsheetTag = scenario.getSourceTagNames().stream()
            .filter(tag -> tag.startsWith("@Data_"))
            .findFirst();

        if (spreadsheetTag.isPresent()) {
            String spreadsheetName = spreadsheetTag.get().replace("@Data_", "");
            String originalPath = "testdata/" + spreadsheetName + ".xlsx";

            try {
                String workingCopyPath = utils.excel.ExcelFileManager.createWorkingCopy(originalPath, "results");
                scenarioContext.set("spreadsheet", workingCopyPath);
                logger.info("🧾 Working spreadsheet stored in context: " + workingCopyPath);
            } catch (RuntimeException e) {
                logger.severe(e.getMessage());
                throw new RuntimeException("❌ Aborting test: missing required testdata file: " + originalPath);
            }
        } else {
            logger.info("ℹ️ No @Data_ tag found for this scenario.");
        }
    }

    public static ScenarioContext getScenarioContext() {
        return scenarioContext;
    }
    
    private static void tidyDir() {
    	try {
    		File currentDir = new File("test-reports"); // base reporting dir
    	    File[] reportDirs = currentDir.listFiles((dir, name) ->
    	    name.matches(".+_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")
    	    );

    	    if (reportDirs != null) {
    	        for (File dir : reportDirs) {
    	            if (!dir.getName().equals(reportDir)) {
    	                long lastModified = dir.lastModified();
    	                long ageInMillis = System.currentTimeMillis() - lastModified;
    	                long twoDaysInMillis = 2L * 24 * 60 * 60 * 1000;

    	                if (ageInMillis > twoDaysInMillis) {
    	                    deleteFolderRecursively(dir.toPath());
    	                    logger.info("🧹 Deleted old report folder: " + dir.getName());
    	                }
    	            }
    	        }
    	    }
    	} catch (Exception e) {
    	    logger.log(Level.WARNING, "⚠️ Failed to clean old report directories: " + e.getMessage(), e);
    	}
    }
    
    private static void deleteFolderRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // delete children first
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        logger.warning("⚠️ Couldn't delete " + p);
                    }
                });
        }
    }
    
    private static String getReportPropertyFromConfig(String key, String defaultValue) {
        try (var configStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties")) {
            if (configStream == null) {
                System.err.println("⚠️ config.properties not found on classpath. Using default value for " + key);
                return defaultValue;
            }

            var props = new java.util.Properties();
            props.load(configStream);
            return props.getProperty(key, defaultValue);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load config.properties: " + e.getMessage() + ". Using default value for " + key);
            return defaultValue;
        }
    }
    
    public static String getReportDirectory() {
        return reportDir;
    }

    private void logoutIfNeeded() {
    try {
    	WebDriver driver = WebDriverManager.getDriver(); 
        LoginPOM loginPage = new LoginPOM(driver);
        loginPage.logOut();
        System.out.println("🔒 Logout successful.");
	    } catch (NoSuchElementException e) {
	        System.out.println("⚠️ Logout element not found, possibly already logged out.");
	    } catch (Exception e) {
	        System.out.println("⚠️ Unexpected error during logout: " + e.getMessage());
	    }
    }
    
}
