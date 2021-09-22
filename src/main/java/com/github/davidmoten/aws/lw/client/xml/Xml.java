package com.github.davidmoten.aws.lw.client.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;

public class Xml {

    private final String name;
    private final Xml parent;
    private Map<String, String> attributes = new HashMap<>();
    private List<Xml> children = new ArrayList<>();
    private String content;
    private boolean prelude = true;

    private Xml(String name) {
        this(name, null);
    }

    private Xml(String name, Xml parent) {
        this.name = name;
        this.parent = parent;
    }

    public static Xml root(String name) {
        return new Xml(name);
    }

    public Xml excludePrelude() {
        Xml xml = this;
        while (xml.parent != null) {
            xml = xml.parent;
        }
        xml.prelude = false;
        return this;
    }

    public Xml element(String name) {
        checkPresent(name, "name");
        Preconditions.checkArgument(content == null,
                "content cannot be already specified if starting a child element");
        Xml xml = new Xml(name, this);
        this.children.add(xml);
        return xml;
    }

    public Xml e(String name) {
        return element(name);
    }

    public Xml attribute(String name, String value) {
        checkPresent(name, "name");
        Preconditions.checkNotNull(value);
        this.attributes.put(name, value);
        return this;
    }

    public Xml a(String name, String value) {
        return attribute(name, value);
    }

    public Xml content(String content) {
        Preconditions.checkArgument(children.isEmpty());
        this.content = content;
        return this;
    }

    public Xml up() {
        return parent;
    }

    private void checkPresent(String s, String name) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must be non-null and non-blank");
        }
    }

    private String toString(String indent) {
        StringBuilder b = new StringBuilder();
        if (indent.length() == 0 && prelude) {
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        // TODO encode attributes and content for xml
        String atts = attributes.entrySet().stream()
                .map(entry -> " " + entry.getKey() + "=\"" + entry.getValue() + "\"")
                .collect(Collectors.joining());
        b.append(String.format("%s<%s%s>", indent, name, atts));
        if (content != null) {
            b.append(content);
            b.append(String.format("</%s>\n", name));
        } else {
            b.append("\n");
            for (Xml xml : children) {
                b.append(xml.toString(indent + "  "));
            }
            b.append(String.format("%s</%s>\n", indent, name));
        }
        return b.toString();
    }

    public String toString() {
        Xml xml = this;
        while (xml.parent != null) {
            xml = xml.parent;
        }
        return xml.toString("");
    }

}
