package search;

import static org.junit.Assert.assertTrue;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
// @RunWith(SpringRunner.class) //@MockBean
// @RunWith(MockitoJUnitRunner.class) //@Mock
// @SpringBootTest

public class SearchTest {

    private ChromeOptions options;
    private WebDriver driver;

    private static final String BOOKS_DIRECTORY = "./resources/books/";

    //    @MockBean // TO BE USED WITH @RunWith(SpringRunner.class)
    //    @Mock  //TO BE USED WITH @RunWith(MockitoJUnitRunner.class)
    //    MappedFieldsService mappedFieldsService;

    //    private Method getSearchWorkerParseWordsFromDocumentMethod() throws NoSuchMethodException {
    //        Method method = SearchWorker.class.getDeclaredMethod("parseWordsFromDocument", Set.class);
    //        method.setAccessible(true);
    //        return method;
    //    }

    //    private Method getSearchWorkerParseWordsFromDocumentMethod() throws NoSuchMethodException {
    //        Method method = SearchWorker.class.getDeclaredMethod("parseWordsFromDocument", String.class);
    //        method.setAccessible(true);
    //        return method;
    //    }
    //
    //    private Method getCreateResultMethod() throws NoSuchMethodException {
    //        Method method = SearchWorker.class.getDeclaredMethod("createResult", Task.class);
    //        method.setAccessible(true);
    //        return method;
    //    }

    // Direct way to use Mockito
    //    @Test
    //    public void givenSearchTerm_ThenReturnBooksRanking()
    //            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    //
    //        SearchWorker searchWorker = Mockito.mock(SearchWorker.class);
    //
    //        Method method = getSearchWorkerParseWordsFromDocumentMethod();
    //        Method methodCreateResult = getCreateResultMethod();
    //        Task task = new Task(
    //                //                Arrays.asList(new String[] {"monster", "martini"}),
    //                Arrays.asList(new String[] {
    //                        "INPUT", "BUTTON", "MAT_SELECT", "MAT_OPTION", "MAT_EXPANSION_PANEL", "ANCHOR", "SELECT",
    // "OPTION"
    //                }),
    //
    //                //                Arrays.asList(new String[] {BOOKS_DIRECTORY + "Frankenstein.txt"}));
    //                Arrays.asList(new String[] {BOOKS_DIRECTORY + "UpstarMusic1.html"}));
    //
    //        Object initial = (methodCreateResult.invoke(searchWorker, task));
    //
    //        assertTrue(initial instanceof Result);
    //        assertTrue(((Result) initial).getDocumentToDocumentData().size() > 0);
    //
    //        //        Object initial = (method.invoke(searchWorker, Set.of(new String[] {
    //        //                "ESB:BAC:1100003", "ESB:BAC:1100002", "ESB:BAC:1100001", "ALL:BAC:1100005",
    // "ESB:BAC:1200001"
    //        //        }));
    //
    //        //        Object initial = (method.invoke(searchWorker, BOOKS_DIRECTORY + "The Adventures of Sherlock
    //        // Holmes.txt"));
    //        //        int rows = ((List<String>) initial).size();
    //    }

    public int getRandomNumberUsingNextInt(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min) + min;
    }

    @Test
    public void CreateDinamicWorkers() throws InterruptedException, IOException {
        int newWorkerPort = getRandomNumberUsingNextInt(49152, 65535);

        String workerPath = String.format(
                "start \"Worker-%s\" java -jar StartWindowMinimized target/lotto.audit.jar %d ",
                newWorkerPort, newWorkerPort, newWorkerPort);
        System.out.println(workerPath);
        if (available(newWorkerPort)) {

            Runtime r = Runtime.getRuntime();
            Process ps = null;
            ps = r.exec(new String[] {"cmd", "/c", workerPath});

            ps.waitFor();
            java.io.InputStream is = ps.getInputStream();
            byte b[] = new byte[is.available()];
            is.read(b, 0, b.length);
            System.out.println(new String(b));

            assertTrue(ps.pid() > 0);
            Thread.sleep(2000);

            // TaskKill All
            // wmic Path win32_process Where "CommandLine Like '%lotto.audit.jar%'" Call Terminate
            // Kill by name/id
            String killProcess = "wmic process Where \"CommandLine Like '%" + newWorkerPort + "%'\" Call Terminate";
            ps = r.exec(new String[] {"cmd", "/c", killProcess});
            assertTrue(ps.pid() > 0);

            // String cmd = "taskkill /F /PID " + ps.pid();
            // ps = r.exec(new String[] {"cmd", "/c", cmd});

        }
    }

    @Before
    public void setup() {
        options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        driver = new ChromeDriver(options);
    }

    @Test
    public void webdiver() {

        driver.get("https://www.fnz.com/contact");

        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        List<javafx.concurrent.Task<Void>> listclickable = new ArrayList<>();
        //        List<WebElement> scannedElementList = new ArrayList<>();
        LinkedHashSet<WebElement> scannedElementList = new LinkedHashSet<WebElement>();
        for (WebElementTagNameEnum tag : clickableTags) {
            List<WebElement> elementList = scanABRElementsByCriteria(driver, By.tagName(tag.getValue()));
            System.out.println(String.format("FOUND FOR %d  %s", elementList.size(), By.tagName(tag.getValue())));
            scannedElementList.addAll(elementList);
            //            for (WebElement element : elementList) {
            //                System.out.println(String.format(
            //                        "Element Found: %s  xpath : %s", element.getTagName(),
            // element.getAttribute("xpath")));
            ////                String fullPath = getFullPath(driver, element);
            ////                System.out.println(String.format(
            ////                        "FULL xpath : %s", fullPath));
            //            }

            // *[@id="firstname-172fd9c0-9f9e-42c2-b0b0-84f5a439b8e5"]
            //            /html/body/main/section[2]/div/div[1]/form/div[1]/div/input
            // *[@id="firstname-172fd9c0-9f9e-42c2-b0b0-84f5a439b8e5"]
            //            Task<Void> task = scanABRElementsAsync(By.tagName(tag.getValue()),
            // ABRWebElement::isNotClickable,
            //                    webElementObservableList, progressBar);
            //
            //            listInputs.add(task);

        }

        //        Supplier<Stream<ABRWebElement>> stream =
        //                () -> scannedElementList.stream().distinct().map(ABRWebElement::new);
    }

    @Test
    public void test_All_Options() {

        // Set up Chrome WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");

        try {
            // Navigate to the webpage
            driver.get("https://www.fnz.com/contact");
            //        driver.get("https://main.marginedge.com/#/restaurantUnit");

            //        // Wait for the page to load completely
            //        WebDriverWait wait = new WebDriverWait(driver, 10);
            //        wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));

            // Get the page source
            String html = driver.getPageSource();

            // Parse the HTML using Jsoup
            Document doc = Jsoup.parse(html);

            // Map to store unique dropdown elements
            Map<String, Element> uniqueSelectElements = new HashMap<>();

            // Find all unique dropdown (select) elements
            Elements selectElements = doc.select("select");
            for (Element select : selectElements) {
                // Determine the name of the select element
                String name = select.attr("name");
                if (name.isEmpty()) {
                    name = select.attr("id");
                }
                // Add the select element to the map
                uniqueSelectElements.put(name, select);
            }

            // Map to store dropdown options
            Map<String, String[]> dropdownOptions = new HashMap<>();

            // Extract options for each unique dropdown element
            for (Map.Entry<String, Element> entry : uniqueSelectElements.entrySet()) {
                Element selectElement = entry.getValue();
                Elements optionElements = selectElement.select("option");
                String[] optionsText = new String[optionElements.size()];
                for (int i = 0; i < optionElements.size(); i++) {
                    optionsText[i] = optionElements.get(i).text().trim();
                }
                dropdownOptions.put(entry.getKey(), optionsText);
            }

            // Print dropdown options
            System.out.println("Dropdown options:");
            for (Map.Entry<String, String[]> entry : dropdownOptions.entrySet()) {
                System.out.println(entry.getKey() + " options: " + String.join(", ", entry.getValue()));
            }

            // Create a list to hold the WebElements
            List<WebElement> webElements = new ArrayList<>();
            // Create a list to hold the Element objects
            List<Element> jsoupElements = new ArrayList<>();

            try {
                // Fetch the HTML content of the page
                Document doc2 =
                        Jsoup.connect("https://www.ca-nextbank.ch/en/contact").get();

                // Select all <a> tags from the page
                Elements links = doc2.select("a[href]");

                // Iterate over the selected links
                for (Element link : links) {
                    // Get the URL and text of the link
                    String url = link.absUrl("href");
                    String text = link.text();

                    // Print the URL and text
                    System.out.println("URL: " + url);
                    System.out.println("Text: " + text);

                    // Add the Element to the list
                    jsoupElements.add(link);

                    // Convert the Element to a WebElement and add it to the list
                    // WebElement webElement = driver.findElementByXPath(link.cssSelector());
                    // webElements.add(webElement);
                }

                // Traverse the list of Element objects and print them
                for (Element element : jsoupElements) {
                    System.out.println("Element: " + element);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            // Attempt to Locate the WebElement to be clickable

            try {

                // Find all elements with href attribute using XPath
                List<WebElement> elementsHRef = driver.findElements(By.xpath("//a[@href]"));

                // Print the href attribute of each element
                for (WebElement element : elementsHRef) {
                    System.out.println("Href: " + element.getAttribute("href"));
                }

                // Iterate over the selected links
                for (Element link : jsoupElements) {
                    // Get the URL of the link
                    String url = link.absUrl("href");

                    // Open the URL in the WebDriver
                    driver.get(url);

                    // Find the WebElement corresponding to the link and add it to the list
                    WebElement webElement = driver.findElement(By.cssSelector("a[href='" + url + "']"));
                    webElements.add(webElement);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close the WebDriver
                driver.quit();
            }

            // Click on each element
            for (WebElement element : webElements) {
                element.click();
            }

        } finally {
            // Close the WebDriver
            driver.quit();
        }
    }

    @Test
    public void test_All_HRefs() {

        // Set up Chrome WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");

        try {
            // Navigate to the webpage
            driver.get("https://www.ca-nextbank.ch/en/contact");
            //        driver.get("https://main.marginedge.com/#/restaurantUnit");

            //        // Wait for the page to load completely
            //        WebDriverWait wait = new WebDriverWait(driver, 10);
            //        wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));

            // Get the page source
            String html = driver.getPageSource();

            // Parse the HTML using Jsoup
            Document doc = Jsoup.parse(html);

            // Create a list to hold the WebElements
            List<WebElement> webElements = new ArrayList<>();
            // Create a list to hold the Element objects
            List<Element> jsoupElements = new ArrayList<>();

            // Fetch the HTML content of the page
            //                Document doc2 =
            //                        Jsoup.connect("https://www.ca-nextbank.ch/en/contact").get();
            // Direct Test
            //            html = "<div>" +
            //                    "<a class=\"baseButton\" href=\"/en/cross-border-commuters\">Cross-border
            // commuters</a>" +
            //                    "<a class=\"baseButton\" href=\"/en/some-other-link\"></a>" +
            //                    "<a class=\"baseButton\" href=\"/en/another-link\">" +
            //                    "<svg class=\"icon\"><use xlink:href=\"#icon-id\"></use></svg></a>" +
            //                    "<a class=\"baseButton baseButton--tertiary-on-dark mr-4 baseButton--icon\"
            // aria-label=\"Facebook\" href=\"https://www.facebook.com/CreditAgricolenextbank/\"><svg
            // stroke=\"currentColor\" fill=\"currentColor\" stroke-width=\"0\" viewBox=\"0 0 24 24\" class=\"shrink-0
            // fill-current\" height=\"24\" width=\"24\" xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M14
            // 13.5H16.5L17.5 9.5H14V7.5C14 6.47062 14 5.5 16 5.5H17.5V2.1401C17.1743 2.09685 15.943 2 14.6429 2C11.9284
            // 2 10 3.65686 10 6.69971V9.5H7V13.5H10V22H14V13.5Z\"></path></svg></a>" +
            //                    "</div>";

            // Parse the HTML string
            //            doc = Jsoup.parse(html);

            // Select all <a> tags from the page
            Elements links = doc.select("a[href]");

            // Iterate over the selected links
            for (Element link : links) {
                // Get the URL and text of the link
                String url = link.absUrl("href");
                String text = link.text();

                // Print the URL and text
                if (Strings.isNullOrEmpty(url)) {
                    url = link.attr("href");
                }

                url = link.attr("href");
                // Check if the text is empty
                if (link.text().isEmpty()) {
                    // Check for nested elements like SVG
                    Element svg = link.selectFirst("svg");
                    if (svg != null && svg.selectFirst("use") != null && svg.hasAttr("xlink:href")) {
                        String svgHref = svg.selectFirst("use").attr("xlink:href");
                        System.out.println("Found SVG with href: " + svgHref + " inside anchor with href: " + url);
                        text = svgHref.toString();
                    } else if (svg != null) {
                        System.out.println("Found anchor with href: " + url + " containing nested SVG.");
                        text = svg.toString();
                    } else {
                        System.out.println("Anchor with href: " + url + " has no text and no nested SVG.");
                    }
                } else {
                    System.out.println("Anchor with href: " + url + " has text: " + link.text());
                }

                System.out.println("URL: " + url);
                System.out.println("Text: " + text);

                // Add the Element to the list
                jsoupElements.add(link);

                // Convert the Element to a WebElement and add it to the list
                // WebElement webElement = driver.findElementByXPath(link.cssSelector());
                // webElements.add(webElement);
            }

            // Traverse the list of Element objects and print them
            for (Element element : jsoupElements) {
                System.out.println("Element: " + element);
            }

            // Attempt to Locate the WebElement to be clickable

            try {

                // Find all elements with href attribute using XPath
                List<WebElement> elementsHRef = driver.findElements(By.xpath("//a[@href]"));

                // Print the href attribute of each element
                for (WebElement element : elementsHRef) {
                    System.out.println("Href: " + element.getAttribute("href"));
                }

                // Iterate over the selected links
                for (Element link : jsoupElements) {
                    // Get the URL of the link
                    String url = link.absUrl("href");

                    // Open the URL in the WebDriver
                    driver.get(url);

                    // Find the WebElement corresponding to the link and add it to the list
                    WebElement webElement = driver.findElement(By.cssSelector("a[href='" + url + "']"));
                    webElements.add(webElement);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close the WebDriver
                driver.quit();
            }

            // Click on each element
            for (WebElement element : webElements) {
                element.click();
            }

        } finally {
            // Close the WebDriver
            driver.quit();
        }
    }

    private List<WebElement> scanABRElementsByCriteria(WebDriver driver, By criteria) {
        try {
            return driver.findElements(criteria);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getFullPath(WebDriver driver, WebElement element) {
        StringBuilder xpathBuilder = new StringBuilder();

        while (element != null) {

            String tagName = element.getTagName();

            int index = 1;

            //            element.findElement(By.xpath("./ancestor-or-self::*")).getAttribute("xpath")
            WebElement sibling = element;
            while ((sibling = sibling.findElement(By.xpath("preceding-sibling::*[name()'" + tagName + "']"))) != null) {
                index++;
            }

            xpathBuilder.insert(0, "/" + tagName + "[" + index + "]");

            element = element.findElement(By.xpath(".."));
        }
        return "//" + xpathBuilder.toString();
    }

    private static boolean available(int port) throws IllegalStateException {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (ConnectException e) {
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Error while trying to check open port", e);
        }
    }
}
