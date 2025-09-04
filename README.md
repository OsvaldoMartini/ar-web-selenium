# AR Web Scanner

This project aims to deliver a product that can register actions
performed on webpages. After registering such actions they can be
executed by the AR Web Engine.

### Summary

* [Setup](#setup)
* [Build Project](#build-project)
* [Contacts](#contacts)
* [Technologies](#technologies)
* [Guide Lines](#guide-lines)
  * [Premise](#premise)
  * [Communication](#communication)
  * [Data Objects](#data-objects)
  * [UI Structure](#ui-structure)

### Setup

* Install maven
* Clone the repository
* Resolve maven dependencies
* Install PostgreSQL
* Create postgres user with correct credentials
* Create HBA database
* Verify configurations in the hibernate.cfg.xml file
* Verify configurations in configuration.properties file
* Build the last version of AR Web Engine _(optional)_
* Move the AR Web Engine artifact "Engine.jar" in the root folder of the project
* Run the command _**mvn javafx:run**_

### Build Project

* **Execute all the steps from [Setup](#setup)**
* Run the command _**mvn clean package**_

### Technologies

* Java 17
* Spring Boot _(to be implemented)_
* Selenium
* JavaFX
* Hibernate
* JPA _(to be implemented)_
* Apache POI
* Shade

### Guide lines

#### Premise
The project tries to make modular components. This is because there
is a possibility in the future to split the application in multiple
applications with different responsibility.
This is because for example: the data fetching from the database is
copied both on the AR Web Scan and the AR Web Engine having class 
duplicated. This causes the problem of having to update classes 
modifications on both projects simultaneously.
To avoid this behaviour we could extract the communication layer into a
separate application that expose services to be consumed by the two 
projects. Another approach could be to build a jar and import it into
the projects.

#### Communication

Right now for communication there is the class Repository which has 
various generics methods to read and write to the database:
```java
public class Repository {
    ...
    public <T> void write(T obj)
    ...
    public <T> void remove(T obj)
    ...
}
```
_**This class is temporary and should be improved by using something
else that is less manual as explained above.**_

#### Data Objects

The data Objects are created using Hibernate. As this is somewhat
standard even for Spring Boot there is really no need to change the
technology used.
As of now the classes have the names explicitly declared. This could
_(and should)_ be changed in the future as sticking to the standard 
Hibernate behaviour is better in case there are breaking updates
that would void the explicitly declared functions.
```java

@Table(name = "bot_job")
//@SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "botJobSeq", allocationSize = 1)
public class BotJobDTO extends BaseDTO implements Serializable {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;
    ...
```
As shown, there is a BaseDTO class as well that is extended from all
the other DTO objects.
Even though it seems a good idea in theory, in practice it creates
numerous problems and is not straightforward for code understanding.
In the future this class should be deleted and the id field
transferred to each class with its correct naming convention.

#### UI Structure

The application begins in the ARControlPanel class.
This class is used just as an entrypoint for the entire application
as it creates an ARMainScene instance and shows it to the user.

The UI components are divided into Panes and Scenes.
Scenes work as the outer layer of a window (width, height, etc..)
Panels on the other hand manage the buttons, inputs, etc... that
the user sees.

Scene class example:
```java
public class ARMainScene extends ARScene {

    private static final Double SCENE_HEIGHT = 30D;
    private static final Double SCENE_WIDTH = 300D;
    private static final String TITLE = "AR Web Scanner";

    public ARMainScene() {
        super();
    }
    ...
}
```

Pane class example:
```java
public class ARMainPane extends ARPane {

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();

    //UI components
    Button newBotJobButton;
    Button viewBotJobButton;
    Button configureButton;
    Button infoButton;
    HBox buttonPane;

    @Override
    public void initUIComponents()
    ...
```

All the Panes and Scenes should extend the ARPane base class as it helps
the development by doing some standard work that should be done on
every Pane/Scene. More explanation can be found on the ARPane base class.

### Contacts

* Osvaldo Martini (PM / Leader Developer)
