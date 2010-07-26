package com.vaadin.tests.layouts.layouttester;

import java.lang.reflect.Method;

import com.vaadin.Application;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.themes.Reindeer;

public class LayoutTesterApplication extends Application {
	private static final long serialVersionUID = 4599974816374406981L;
	Button nextButton = new Button("Next");
	private int layoutIndex = -1;
	private int layoutCount = 1;
	
	private Method[] layoutGetters;
	private Window mainWindow;
	private NativeSelect layoutSelector;
	
	@Override
	public void init() {
		mainWindow = new Window("LayoutTesterApplication");
		setMainWindow(mainWindow);
		loadLayoutGetters();
		nextLaytout();
		
		nextButton.addListener(new Button.ClickListener() {
			private static final long serialVersionUID = -1577298910202253538L;
			@Override
			public void buttonClick(ClickEvent event) {
				nextLaytout();
			}
		});
	}
	
	private void nextLaytout() {
		try {
			mainWindow.removeAllComponents();
			HorizontalLayout vlo = new HorizontalLayout();
			vlo.setSpacing(true);
			++layoutIndex;
			if (layoutIndex>=layoutCount) {
				layoutIndex=0;
			}
			mainWindow.addComponent(vlo);
			vlo.addComponent(nextButton);
			vlo.addComponent(getLayoutTypeSelect());
			vlo.addComponent(new Label(layoutGetters[layoutIndex].getName()));
			
			Layout lo = null;
			if (layoutSelector.getValue()==VerticalLayout.class) {
				lo = getVerticalTestLayout(layoutIndex);
			}
			else if (layoutSelector.getValue()==HorizontalLayout.class) {
				lo = getHorizontalTestLayout(layoutIndex);
			}
			else if (layoutSelector.getValue()==GridLayout.class) {
				lo = getGridTestLayout(layoutIndex);
			}
			if (lo!=null) {
				lo.addStyleName(Reindeer.LAYOUT_BLUE);
				mainWindow.addComponent(lo);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public void loadLayoutGetters() {
		layoutGetters = AbstractLayoutTests.class.getDeclaredMethods();
		layoutCount = layoutGetters.length;
	}
	
	public Layout getVerticalTestLayout(int index) throws Exception {
		VerticalLayoutTests vlotest = new VerticalLayoutTests(this);
		return (Layout) layoutGetters[index].invoke(vlotest, (Object[])null);
	}
	public Layout getHorizontalTestLayout(int index) throws Exception {
		HorizontalLayoutTests hlotest = new HorizontalLayoutTests(this);
		return (Layout) layoutGetters[index].invoke(hlotest, (Object[])null);
	}
	public Layout getGridTestLayout(int index) throws Exception {
		GridLayoutTests hlotest = new GridLayoutTests(this);
		return (Layout) layoutGetters[index].invoke(hlotest, (Object[])null);
	}
	
	private NativeSelect getLayoutTypeSelect() {
		if (layoutSelector==null) {
			layoutSelector = new NativeSelect();
			layoutSelector.addItem(VerticalLayout.class);
			layoutSelector.addItem(HorizontalLayout.class);
			layoutSelector.addItem(GridLayout.class);
			layoutSelector.setNullSelectionAllowed(false);
			layoutSelector.setImmediate(true);
			layoutSelector.select(VerticalLayout.class);
			layoutSelector.addListener(new Property.ValueChangeListener() {
				private static final long serialVersionUID = -605319614765838359L;
				@Override
				public void valueChange(ValueChangeEvent event) {
					layoutIndex=-1;
					nextLaytout();
				}
			});
		}
		return layoutSelector;
	}

}
