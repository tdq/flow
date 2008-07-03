/* 
@ITMillApache2LicenseForJavaFiles@
 */

package com.itmill.toolkit.terminal.gwt.client.ui;

import java.util.Iterator;
import java.util.Vector;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.itmill.toolkit.terminal.gwt.client.ApplicationConnection;
import com.itmill.toolkit.terminal.gwt.client.BrowserInfo;
import com.itmill.toolkit.terminal.gwt.client.Caption;
import com.itmill.toolkit.terminal.gwt.client.Container;
import com.itmill.toolkit.terminal.gwt.client.ContainerResizedListener;
import com.itmill.toolkit.terminal.gwt.client.Paintable;
import com.itmill.toolkit.terminal.gwt.client.StyleConstants;
import com.itmill.toolkit.terminal.gwt.client.UIDL;
import com.itmill.toolkit.terminal.gwt.client.Util;

/**
 * Abstract base class for ordered layouts. Use either vertical or horizontal
 * subclass.
 * 
 * @author IT Mill Ltd
 */
public class IOrderedLayout extends Panel implements Container,
        ContainerResizedListener {

    public static final String CLASSNAME = "i-orderedlayout";

    public static final int ORIENTATION_VERTICAL = 0;
    public static final int ORIENTATION_HORIZONTAL = 1;

    // TODO Read this from CSS as in #1904
    private static final int SPACING_SIZE = 8;

    int orientationMode = ORIENTATION_VERTICAL;

    protected ApplicationConnection client;

    /**
     * Reference to Element where wrapped childred are contained. Normally a TR
     * or a TBODY element.
     */
    private Element wrappedChildContainer;

    /**
     * Elements that provides the Layout interface implementation. Root element
     * of the component. In vertical mode this is the outmost div.
     */
    private final Element root;

    /**
     * Margin element of the component. In vertical mode, this is div inside
     * root.
     */
    protected Element margin;

    /**
     * List of child widgets. This is not the list of wrappers, but the actual
     * widgets
     */
    private final Vector childWidgets = new Vector();

    /**
     * Fixed cell-size mode is used when height/width is explicitly given for
     * vertical/horizontal orderedlayout.
     */
    private boolean fixedCellSize = false;

    /**
     * List of child widget wrappers. These wrappers are in exact same indexes
     * as the widgets in childWidgets list.
     */
    private final Vector childWidgetWrappers = new Vector();

    /** Whether the component has spacing enabled. */
    private boolean hasComponentSpacing;

    /** Whether the component has spacing enabled. */
    private int previouslyAppliedFixedSize = -1;

    /** Information about margin states. */
    private MarginInfo margins = new MarginInfo(0);

    /**
     * Construct the DOM of the orderder layout.
     * 
     * <p>
     * There are two modes - vertical and horizontal.
     * <ul>
     * <li>Vertical mode uses structure: div-root ( div-margin-childcontainer (
     * div-wrap ( child ) div-wrap ( child )))).</li>
     * <li>Horizontal mode uses structure: div-root ( div-margin ( table (
     * tbody ( tr-childcontainer ( td-wrap ( child ) td-wrap ( child) )) )</li>
     * </ul>
     * where root, margin and childcontainer refer to the root element, margin
     * element and the element that contain WidgetWrappers.
     * </p>
     * 
     */
    public IOrderedLayout() {

        root = DOM.createDiv();
        margin = DOM.createDiv();
        DOM.appendChild(root, margin);
        DOM.setStyleAttribute(margin, "overflow", "hidden");
        createAndEmptyWrappedChildContainer();
        setElement(root);
        setStyleName(CLASSNAME);
    }

    private void createAndEmptyWrappedChildContainer() {
        if (orientationMode == ORIENTATION_HORIZONTAL) {
            final String structure = "<table cellspacing=\"0\" cellpadding=\"0\"><tbody><tr></tr></tbody></table>";
            DOM.setInnerHTML(margin, structure);
            wrappedChildContainer = DOM.getFirstChild(DOM.getFirstChild(DOM
                    .getFirstChild(margin)));
        } else {
            wrappedChildContainer = margin;
            DOM.setInnerHTML(margin, "");
        }
    }

    /** Update orientation, if it has changed */
    private void updateOrientation(int newOrientationMode) {

        // Only change when needed
        if (orientationMode == newOrientationMode) {
            return;
        }

        orientationMode = newOrientationMode;

        createAndEmptyWrappedChildContainer();

        // Reinsert all widget wrappers to this container
        for (int i = 0; i < childWidgetWrappers.size(); i++) {
            DOM.appendChild(wrappedChildContainer,
                    ((WidgetWrapper) childWidgetWrappers.get(i)).getElement());
        }

        Util.runDescendentsLayout(this);
    }

    /** Update the contents of the layout from UIDL. */
    public void updateFromUIDL(UIDL uidl, ApplicationConnection client) {

        this.client = client;

        updateOrientation("horizontal".equals(uidl
                .getStringAttribute("orientation")) ? ORIENTATION_HORIZONTAL
                : ORIENTATION_VERTICAL);

        // Ensure correct implementation
        if (client.updateComponent(this, uidl, false)) {
            return;
        }

        // Handle layout margins
        if (margins.getBitMask() != uidl.getIntAttribute("margins")) {
            handleMargins(uidl);
        }

        // Handle component spacing later in handleAlignments() method
        hasComponentSpacing = uidl.getBooleanAttribute("spacing");

        // Collect the list of contained widgets after this update
        final Vector newWidgets = new Vector();
        for (final Iterator it = uidl.getChildIterator(); it.hasNext();) {
            final UIDL uidlForChild = (UIDL) it.next();
            final Paintable child = client.getPaintable(uidlForChild);
            newWidgets.add(child);
        }

        // Iterator for old widgets
        final Iterator oldWidgetsIterator = (new Vector(childWidgets))
                .iterator();

        // Iterator for new widgets
        final Iterator newWidgetsIterator = newWidgets.iterator();

        // Iterator for new UIDL
        final Iterator newUIDLIterator = uidl.getChildIterator();

        // List to collect all now painted widgets to in order to remove
        // unpainted ones later
        final Vector paintedWidgets = new Vector();

        // Add any new widgets to the ordered layout
        Widget oldChild = null;
        while (newWidgetsIterator.hasNext()) {

            final Widget newChild = (Widget) newWidgetsIterator.next();
            final UIDL newChildUIDL = (UIDL) newUIDLIterator.next();

            // Remove any unneeded old widgets
            if (oldChild == null && oldWidgetsIterator.hasNext()) {
                // search for next old Paintable which still exists in layout
                // and delete others
                while (oldWidgetsIterator.hasNext()) {
                    oldChild = (Widget) oldWidgetsIterator.next();
                    // now oldChild is an instance of Paintable
                    if (paintedWidgets.contains(oldChild)) {
                        continue;
                    } else if (newWidgets.contains(oldChild)) {
                        break;
                    } else {
                        remove(oldChild);
                        oldChild = null;
                    }
                }
            }

            if (oldChild == null) {
                // we are adding components to the end of layout
                add(newChild);
            } else if (newChild == oldChild) {
                // child already attached in correct position
                oldChild = null;
            } else if (hasChildComponent(newChild)) {

                // current child has been moved, re-insert before current
                // oldChild
                add(newChild, childWidgets.indexOf(oldChild));

            } else {
                // insert new child before old one
                add(newChild, childWidgets.indexOf(oldChild));
            }

            // Update the child component
            ((Paintable) newChild).updateFromUIDL(newChildUIDL, client);

            // Add this newly handled component to the list of painted
            // components
            paintedWidgets.add(newChild);
        }

        // Remove possibly remaining old widgets which were not in painted UIDL
        while (oldWidgetsIterator.hasNext()) {
            oldChild = (Widget) oldWidgetsIterator.next();
            if (!newWidgets.contains(oldChild)) {
                remove(oldChild);
            }
        }

        // Handle component alignments
        handleAlignments(uidl);

        updateFixedSizes();
    }

    public void setWidth(String width) {
        super.setWidth(width);

        if (width == null || "".equals(width)) {
            DOM.setStyleAttribute(margin, "width", "");
            if (fixedCellSize && orientationMode == ORIENTATION_HORIZONTAL) {
                removeFixedSizes();
            }
        } else {
            DOM.setStyleAttribute(margin, "width", "100%");
            if (orientationMode == ORIENTATION_HORIZONTAL) {
                fixedCellSize = true;
            }
        }
    }

    public void setHeight(String height) {
        super.setHeight(height);

        if (height == null || "".equals(height)) {
            DOM.setStyleAttribute(margin, "height", "");

            // Removing fixed size is needed only when it is in use
            if (fixedCellSize && orientationMode == ORIENTATION_VERTICAL) {
                removeFixedSizes();
            }
        } else {
            DOM.setStyleAttribute(margin, "height", "100%");
            if (orientationMode == ORIENTATION_VERTICAL) {
                fixedCellSize = true;
            }
        }
    }

    /** Remove fixed sizes from use */
    private void removeFixedSizes() {

        // If already removed, do not do it twice
        if (!fixedCellSize) {
            return;
        }

        // Remove unneeded attributes from each wrapper
        String wh = (orientationMode == ORIENTATION_HORIZONTAL) ? "width"
                : "height";
        String overflow = (orientationMode == ORIENTATION_HORIZONTAL) ? (BrowserInfo
                .get().isFF2() ? "overflow" : "overflowX")
                : "overflowY";
        for (Iterator i = childWidgetWrappers.iterator(); i.hasNext();) {
            Element we = ((WidgetWrapper) i.next()).getElement();
            DOM.setStyleAttribute(we, wh, "");
            DOM.setStyleAttribute(we, "overflow", "");
        }

        // margin
        DOM.setStyleAttribute(margin,
                (orientationMode == ORIENTATION_HORIZONTAL) ? "width"
                        : "height", "");

        // Remove unneeded attributes from horizontal layouts table
        if (orientationMode == ORIENTATION_HORIZONTAL) {
            Element table = DOM.getParent(DOM.getParent(wrappedChildContainer));
            DOM.setStyleAttribute(table, "tableLayout", "auto");
            DOM.setStyleAttribute(table, "width", "");
        }

        fixedCellSize = false;
        previouslyAppliedFixedSize = -1;

    }

    /** Reset the fixed cell-sizes for children. */
    private void updateFixedSizes() {

        // Do not do anything if we really should not be doing this
        if (!fixedCellSize) {
            return;
        }

        DOM.setStyleAttribute(margin,
                (orientationMode == ORIENTATION_HORIZONTAL) ? "width"
                        : "height", "100%");

        int size = DOM.getElementPropertyInt(margin,
                (orientationMode == ORIENTATION_HORIZONTAL) ? "offsetWidth"
                        : "offsetHeight");

        // Horizontal layouts need fixed mode tables
        if (orientationMode == ORIENTATION_HORIZONTAL) {
            Element table = DOM.getParent(DOM.getParent(wrappedChildContainer));
            DOM.setStyleAttribute(table, "tableLayout", "fixed");
            DOM.setStyleAttribute(table, "width", "" + size + "px");
        }

        // Reduce spacing from the size
        int numChild = childWidgets.size();
        if (hasComponentSpacing) {
            size -= SPACING_SIZE * (numChild - 1);
        }

        // Have we set fixed sizes before?
        boolean firstTime = (previouslyAppliedFixedSize < 0);

        // If so, are they already correct?
        if (size == previouslyAppliedFixedSize) {
            return;
        }
        previouslyAppliedFixedSize = size;

        // Set the sizes for each child
        String wh = (orientationMode == ORIENTATION_HORIZONTAL) ? "width"
                : "height";
        String overflow = (orientationMode == ORIENTATION_HORIZONTAL) ? (BrowserInfo
                .get().isFF2() ? "overflow" : "overflowX")
                : "overflowY";
        for (Iterator i = childWidgetWrappers.iterator(); i.hasNext();) {
            Element we = ((WidgetWrapper) i.next()).getElement();
            final int ws = Math.round(((float) size) / (numChild--));
            size -= ws;
            DOM.setStyleAttribute(we, wh, "" + ws + "px");
            if (firstTime) {
                DOM.setStyleAttribute(we, "overflow", "hidden");
            }
        }

        fixedCellSize = true;

        Util.runDescendentsLayout(this);
    }

    protected void handleMargins(UIDL uidl) {
        margins = new MarginInfo(uidl.getIntAttribute("margins"));
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_TOP,
                margins.hasTop());
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_RIGHT,
                margins.hasRight());
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_BOTTOM,
                margins.hasBottom());
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_LEFT,
                margins.hasLeft());
    }

    private void handleAlignments(UIDL uidl) {
        // Component alignments as a comma separated list.
        // See com.itmill.toolkit.terminal.gwt.client.ui.AlignmentInfo.java for
        // possible values.
        final int[] alignments = uidl.getIntArrayAttribute("alignments");
        int alignmentIndex = 0;

        // Insert alignment attributes
        final Iterator it = childWidgetWrappers.iterator();

        while (it.hasNext()) {

            // Calculate alignment info
            final AlignmentInfo ai = new AlignmentInfo(
                    alignments[alignmentIndex++]);

            final WidgetWrapper wr = (WidgetWrapper) it.next();

            wr.setAlignment(ai.getVerticalAlignment(), ai
                    .getHorizontalAlignment());

            // Handle spacing in this loop as well
            wr.setSpacingEnabled(alignmentIndex == 1 ? false
                    : hasComponentSpacing);
        }
    }

    /**
     * WidgetWrapper classe. Helper classe for spacing and alignment handling.
     * 
     */
    class WidgetWrapper extends UIObject {

        Element td;
        Caption caption = null;

        public WidgetWrapper() {
            if (orientationMode == ORIENTATION_VERTICAL) {
                setElement(DOM.createDiv());
                // Apply 'hasLayout' for IE (needed to get accurate dimension
                // calculations)
                if (BrowserInfo.get().isIE()) {
                    DOM.setStyleAttribute(getElement(), "zoom", "1");
                }
            } else {
                setElement(DOM.createTD());
            }
        }

        public void updateCaption(UIDL uidl, Paintable paintable) {
            final Widget widget = (Widget) paintable;
            if (Caption.isNeeded(uidl)) {
                boolean justAdded = false;
                if (caption == null) {
                    justAdded = true;
                    caption = new Caption(paintable, client);
                }
                caption.updateCaption(uidl);
                final boolean after = caption.shouldBePlacedAfterComponent();
                final Element captionElement = caption.getElement();
                final Element widgetElement = widget.getElement();
                if (justAdded) {
                    if (after) {
                        DOM.appendChild(getElement(), captionElement);
                        DOM.setElementAttribute(getElement(), "class",
                                "i-orderedlayout-w");
                        caption.addStyleName("i-orderedlayout-c");
                        widget.addStyleName("i-orderedlayout-w-e");
                    } else {
                        DOM.insertChild(getElement(), captionElement, 0);
                    }

                } else
                // Swap caption and widget if needed or add
                if (after == (DOM.getChildIndex(getElement(), widgetElement) > DOM
                        .getChildIndex(getElement(), captionElement))) {
                    Element firstElement = DOM.getChild(getElement(), DOM
                            .getChildCount(getElement()) - 2);
                    DOM.removeChild(getElement(), firstElement);
                    DOM.appendChild(getElement(), firstElement);
                    DOM.setElementAttribute(getElement(), "class",
                            after ? "i-orderedlayout-w" : "");
                    if (after) {
                        caption.addStyleName("i-orderedlayout-c");
                        widget.addStyleName("i-orderedlayout-w-e");
                    } else {
                        widget.removeStyleName("i-orderedlayout-w-e");
                        caption.removeStyleName("i-orderedlayout-w-c");
                    }
                }

            } else {
                if (caption != null) {
                    DOM.removeChild(getElement(), caption.getElement());
                    caption = null;
                    DOM.setElementAttribute(getElement(), "class", "");
                    widget.removeStyleName("i-orderedlayout-w-e");
                    caption.removeStyleName("i-orderedlayout-w-c");
                }
            }
        }

        Element getContainerElement() {
            if (td != null) {
                return td;
            } else {
                return getElement();
            }
        }

        /**
         * Set alignments for this wrapper.
         */
        void setAlignment(String verticalAlignment, String horizontalAlignment) {

            // Set vertical alignment

            if (BrowserInfo.get().isIE()) {
                DOM.setElementAttribute(getElement(), "vAlign",
                        verticalAlignment);
            } else {
                DOM.setStyleAttribute(getElement(), "verticalAlign",
                        verticalAlignment);
            }

            // Set horizontal alignment

            // use one-cell table to implement horizontal alignments, only
            // for values other than "left" (which is default)
            // build one cell table
            if (!horizontalAlignment.equals("left")) {
                if (td == null) {

                    // The previous positioning has been left (or unspecified).
                    // Thus we need to create a one-cell-table to position
                    // this element.

                    // Store and remove the current childs (widget and caption)
                    Element c1 = DOM.getFirstChild(getElement());
                    DOM.removeChild(getElement(), c1);
                    Element c2 = DOM.getFirstChild(getElement());
                    if (c2 != null) {
                        DOM.removeChild(getElement(), c2);
                    }

                    // Construct table structure to align children
                    final String t = "<table cellpadding='0' cellspacing='0' width='100%'><tbody><tr><td>"
                            + "<table cellpadding='0' cellspacing='0' ><tbody><tr><td align='left'>"
                            + "</td></tr></tbody></table></td></tr></tbody></table>";
                    DOM.setInnerHTML(getElement(), t);
                    td = DOM.getFirstChild(DOM.getFirstChild(DOM
                            .getFirstChild(DOM.getFirstChild(getElement()))));
                    Element itd = DOM.getFirstChild(DOM.getFirstChild(DOM
                            .getFirstChild(DOM.getFirstChild(td))));

                    // Restore children inside the
                    DOM.appendChild(itd, c1);
                    if (c2 != null) {
                        DOM.appendChild(itd, c2);
                    }

                } else {

                    // Go around optimization bug in WebKit and ensure repaint
                    if (BrowserInfo.get().isSafari()) {
                        String prevValue = DOM.getElementAttribute(td, "align");
                        if (!horizontalAlignment.equals(prevValue)) {
                            Element parent = DOM.getParent(td);
                            DOM.removeChild(parent, td);
                            DOM.appendChild(parent, td);
                        }
                    }

                }
                DOM.setElementAttribute(td, "align", horizontalAlignment);

            } else if (td != null) {

                // In this case we are requested to position this left
                // while as it has had some other position in the past.
                // Thus the one-cell wrapper table must be removed.

                // Move content to main container
                Element itd = DOM.getFirstChild(DOM.getFirstChild(DOM
                        .getFirstChild(DOM.getFirstChild(td))));
                while (DOM.getChildCount(itd) > 0) {
                    Element content = DOM.getFirstChild(itd);
                    if (content != null) {
                        DOM.removeChild(itd, content);
                        DOM.appendChild(getElement(), content);
                    }
                }

                // Remove unneeded table element
                DOM.removeChild(getElement(), DOM.getFirstChild(getElement()));

                td = null;
            }
        }

        void setSpacingEnabled(boolean b) {
            setStyleName(
                    getElement(),
                    CLASSNAME
                            + "-"
                            + (orientationMode == ORIENTATION_HORIZONTAL ? StyleConstants.HORIZONTAL_SPACING
                                    : StyleConstants.VERTICAL_SPACING), b);
        }

    }

    /* documented at super */
    public void add(Widget child) {
        add(child, childWidgets.size());
    }

    /**
     * Add widget to this layout at given position.
     * 
     * This methods supports reinserting exiting child into layout - it just
     * moves the position of the child in the layout.
     */
    public void add(Widget child, int atIndex) {
        /*
         * <b>Validate:</b> Perform any sanity checks to ensure the Panel can
         * accept a new Widget. Examples: checking for a valid index on
         * insertion; checking that the Panel is not full if there is a max
         * capacity.
         */
        if (atIndex < 0 || atIndex > childWidgets.size()) {
            return;
        }

        /*
         * <b>Adjust for Reinsertion:</b> Some Panels need to handle the case
         * where the Widget is already a child of this Panel. Example: when
         * performing a reinsert, the index might need to be adjusted to account
         * for the Widget's removal. See
         * {@link ComplexPanel#adjustIndex(Widget, int)}.
         */
        if (childWidgets.contains(child)) {
            if (childWidgets.indexOf(child) == atIndex) {
                return;
            }

            final int removeFromIndex = childWidgets.indexOf(child);
            final WidgetWrapper wrapper = (WidgetWrapper) childWidgetWrappers
                    .get(removeFromIndex);
            Element wrapperElement = wrapper.getElement();
            final int nonWidgetChildElements = DOM
                    .getChildCount(wrappedChildContainer)
                    - childWidgets.size();
            DOM.removeChild(wrappedChildContainer, wrapperElement);
            DOM.insertChild(wrappedChildContainer, wrapperElement, atIndex
                    + nonWidgetChildElements);
            childWidgets.remove(removeFromIndex);
            childWidgetWrappers.remove(removeFromIndex);
            childWidgets.insertElementAt(child, atIndex);
            childWidgetWrappers.insertElementAt(wrapper, atIndex);
            return;
        }

        /*
         * <b>Detach Child:</b> Remove the Widget from its existing parent, if
         * any. Most Panels will simply call {@link Widget#removeFromParent()}
         * on the Widget.
         */
        child.removeFromParent();

        /*
         * <b>Logical Attach:</b> Any state variables of the Panel should be
         * updated to reflect the addition of the new Widget. Example: the
         * Widget is added to the Panel's {@link WidgetCollection} at the
         * appropriate index.
         */
        childWidgets.insertElementAt(child, atIndex);

        /*
         * <b>Physical Attach:</b> The Widget's Element must be physically
         * attached to the Panel's Element, either directly or indirectly.
         */
        final WidgetWrapper wrapper = new WidgetWrapper();
        final int nonWidgetChildElements = DOM
                .getChildCount(wrappedChildContainer)
                - childWidgetWrappers.size();
        childWidgetWrappers.insertElementAt(wrapper, atIndex);
        DOM.insertChild(wrappedChildContainer, wrapper.getElement(), atIndex
                + nonWidgetChildElements);
        DOM.appendChild(wrapper.getElement(), child.getElement());

        /*
         * <b>Adopt:</b> Call {@link #adopt(Widget)} to finalize the add as the
         * very last step.
         */
        adopt(child);
    }

    /* documented at super */
    public boolean remove(Widget child) {

        /*
         * <b>Validate:</b> Make sure this Panel is actually the parent of the
         * child Widget; return <code>false</code> if it is not.
         */
        if (!childWidgets.contains(child)) {
            return false;
        }

        /*
         * <b>Orphan:</b> Call {@link #orphan(Widget)} first while the child
         * Widget is still attached.
         */
        orphan(child);

        /*
         * <b>Physical Detach:</b> Adjust the DOM to account for the removal of
         * the child Widget. The Widget's Element must be physically removed
         * from the DOM.
         */
        final int index = childWidgets.indexOf(child);
        final WidgetWrapper wrapper = (WidgetWrapper) childWidgetWrappers
                .get(index);
        DOM.removeChild(wrappedChildContainer, wrapper.getElement());
        childWidgetWrappers.remove(index);

        /*
         * <b>Logical Detach:</b> Update the Panel's state variables to reflect
         * the removal of the child Widget. Example: the Widget is removed from
         * the Panel's {@link WidgetCollection}.
         */
        childWidgets.remove(index);

        return true;
    }

    /* documented at super */
    public boolean hasChildComponent(Widget component) {
        return childWidgets.contains(component);
    }

    /* documented at super */
    public void replaceChildComponent(Widget oldComponent, Widget newComponent) {
        final int index = childWidgets.indexOf(oldComponent);
        if (index >= 0) {
            client.unregisterPaintable((Paintable) oldComponent);
            remove(oldComponent);
            add(newComponent, index);
        }
    }

    /* documented at super */
    public void updateCaption(Paintable component, UIDL uidl) {
        final int index = childWidgets.indexOf(component);
        if (index >= 0) {
            ((WidgetWrapper) childWidgetWrappers.get(index)).updateCaption(
                    uidl, component);
        }
    }

    /* documented at super */
    public Iterator iterator() {
        return childWidgets.iterator();
    }

    /* documented at super */
    public void iLayout() {
        updateFixedSizes();
        Util.runDescendentsLayout(this);
    }
}
