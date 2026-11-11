package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** The other end of Widget's to-many relation. */
@Entity
public class WidgetPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Widget widget;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Widget getWidget() { return widget; }
    public void setWidget(Widget widget) { this.widget = widget; }
}
