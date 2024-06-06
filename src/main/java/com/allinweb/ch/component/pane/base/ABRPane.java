package com.allinweb.ch.component.pane.base;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

/***
 * This abstract class is the default class to be extended by other ABRPanes created.
 * This class performs some default actions to improve development times and behaviour management.
 * It also provides some methods to separate UI creation management and UI behaviour management.
 *
 * Attention: Modifying this class can cause major breaks in the code so edit only if you know what you are doing
 * and vastly analyzed the solution
 */
public abstract class ABRPane implements IABRPane {

    // This is the pane that will be created by the extending class
    private Pane pane;

    /***
     * This method creates the instance Pane by using the internal initUI method.
     * This is the method that is exposed outside to create the actual pane.
     * It should not be overridden.
     * @return Returns the Pane created
     */
    @Override
    public final Pane createPane() {
        initUI();
        return pane;
    }

    /***
     * This abstract method has the role of getting the reference of the extending class Pane that has been
     * chosen. It should reference the final pane constructed that needs to be shown.
     * @return Returns the Pane reference chosen in the extending class
     */
    public abstract Pane getPaneReference();

    /***
     * This method uses the overridden methods of the extending class to initialize all the components
     * after initializing the components and their behaviours, it saves the pane reference to be returned
     */
    private void initUI() {
        initUIComponents();
        initUIBehaviour();
        pane = getPaneReference();
    }

    /***
     * This method is a utility method that can add various Nodes to a Pane.
     * As the Pane is generic, they are added with a default behaviour, without considering the
     * various implementation.
     * @param panel The Pane to which the Nodes will be added
     * @param toAdd List of Nodes to be added
     */
    @Override
    public final void addNodesToPane(Pane panel, Node... toAdd) {
        for (Node node : toAdd) {
            if (!panel.getChildren().contains(node)) {
                panel.getChildren().add(node);
            }
        }
    }

    @Override
    public void clearPane(Pane panel) {
        panel.getChildren().removeAll(panel.getChildren());
    }

    @Override
    public final void removeNodesFromPane(Pane panel, Node... toRemove) {
        panel.getChildren().removeAll(toRemove);
    }
}
