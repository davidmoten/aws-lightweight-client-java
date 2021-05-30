/* XMLElement.java
 *
 * $Revision: 1.4 $
 * $Date: 2002/03/24 10:27:59 $
 * $Name: RELEASE_2_2_1 $
 *
 * This file is part of NanoXML 2 Lite.
 * Copyright (C) 2000-2002 Marc De Scheemaecker, All Rights Reserved.
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the
 * use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software in
 *     a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *
 *  3. This notice may not be removed or altered from any source distribution.
 *****************************************************************************/

// ALTERED greatly by Dave Moten May 2021

package nanoxml;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * XMLElement is a representation of an XML object. The object is able to parse
 * XML code.
 * <P>
 * <DL>
 * <DT><B>Parsing XML Data</B></DT>
 * <DD>You can parse XML data using the following code:
 * <UL>
 * <CODE>
 * XMLElement xml = new XMLElement();<BR>
 * FileReader reader = new FileReader("filename.xml");<BR>
 * xml.parseFromReader(reader);
 * </CODE>
 * </UL>
 * </DD>
 * </DL>
 * <DL>
 * <DT><B>Retrieving Attributes</B></DT>
 * <DD>You can enumerate the attributes of an element using the method
 * {@link #enumerateAttributeNames() enumerateAttributeNames}. The attribute
 * values can be retrieved using the method
 * {@link #getStringAttribute(java.lang.String) getStringAttribute}. The
 * following example shows how to list the attributes of an element:
 * <UL>
 * <CODE>
 * XMLElement element = ...;<BR>
 * Enumeration enum = element.getAttributeNames();<BR>
 * while (enum.hasMoreElements()) {<BR>
 * &nbsp;&nbsp;&nbsp;&nbsp;String key = (String) enum.nextElement();<BR>
 * &nbsp;&nbsp;&nbsp;&nbsp;String value = element.getStringAttribute(key);<BR>
 * &nbsp;&nbsp;&nbsp;&nbsp;System.out.println(key + " = " + value);<BR>
 * }
 * </CODE>
 * </UL>
 * </DD>
 * </DL>
 * <DL>
 * <DT><B>Retrieving Child Elements</B></DT>
 * <DD>You can enumerate the children of an element using
 * {@link #enumerateChildren() enumerateChildren}. The number of child elements
 * can be retrieved using {@link #countChildren() countChildren}.</DD>
 * </DL>
 * <DL>
 * <DT><B>Elements Containing Character Data</B></DT>
 * <DD>If an elements contains character data, like in the following example:
 * <UL>
 * <CODE>
 * &lt;title&gt;The Title&lt;/title&gt;
 * </CODE>
 * </UL>
 * you can retrieve that data using the method {@link #getContent() getContent}.
 * </DD>
 * </DL>
 * <DL>
 * <DT><B>Subclassing XMLElement</B></DT>
 * <DD>When subclassing XMLElement, you need to override the method
 * {@link #createAnotherElement() createAnotherElement} which has to return a
 * new copy of the receiver.</DD>
 * </DL>
 * <P>
 *
 */
public final class XMLElement {

    private Map<String, String> attributes;
    private List<XMLElement> children;
    private String name;

    /**
     * The #PCDATA content of the object. null if no #PCDATA, can be empty string
     */
    private String contents;

    /**
     * Conversion table for &amp;...; entities. The keys are the entity names
     * without the &amp; and ; delimiters.
     *
     * <dl>
     * <dt><b>Invariants:</b></dt>
     * <dd>
     * <ul>
     * <li>The field is never <code>null</code>.
     * <li>The field always contains the following associations:
     * "lt"&nbsp;=&gt;&nbsp;"&lt;", "gt"&nbsp;=&gt;&nbsp;"&gt;",
     * "quot"&nbsp;=&gt;&nbsp;"\"", "apos"&nbsp;=&gt;&nbsp;"'",
     * "amp"&nbsp;=&gt;&nbsp;"&amp;"
     * <li>The keys are strings
     * <li>The values are char arrays
     * </ul>
     * </dd>
     * </dl>
     */
    private Map<String, char[]> entities;

    /**
     * The line number where the element starts.
     *
     */
    private int lineNr;

    /**
     * <code>true</code> if the leading and trailing whitespace of #PCDATA sections
     * have to be ignored.
     */
    private boolean ignoreWhitespace;

    /**
     * Character read too much. This character provides push-back functionality to
     * the input reader without having to use a PushbackReader. If there is no such
     * character, this field is '\0'.
     */
    private char charReadTooMuch;

    /**
     * The reader provided by the caller of the parse method.
     *
     * <dl>
     * <dt><b>Invariants:</b></dt>
     * <dd>
     * <ul>
     * <li>The field is not <code>null</code> while the parse method is running.
     * </ul>
     * </dd>
     * </dl>
     */
    private Reader reader;

    /**
     * The current line number in the source content.
     *
     * <dl>
     * <dt><b>Invariants:</b></dt>
     * <dd>
     * <ul>
     * <li>parserLineNr &gt; 0 while the parse method is running.
     * </ul>
     * </dd>
     * </dl>
     */
    private int parserLineNr;

    public XMLElement() {
        this(new HashMap<>(), false, true);
    }

    /**
     * Creates and initializes a new XML element.
     * <P>
     * This constructor should <I>only</I> be called from
     * {@link #createAnotherElement() createAnotherElement} to create child
     * elements.
     *
     * @param entities                 The entity conversion table.
     * @param skipLeadingWhitespace    <code>true</code> if leading and trailing
     *                                 whitespace in PCDATA content has to be
     *                                 removed.
     * @param fillBasicConversionTable <code>true</code> if the basic entities need
     *                                 to be added to the entity list.
     *
     * @see nanoxml.XMLElement#createAnotherElement()
     */
    protected XMLElement(Map<String, char[]> entities, boolean skipLeadingWhitespace,
            boolean fillBasicConversionTable) {
        this.ignoreWhitespace = skipLeadingWhitespace;
        this.name = null;
        this.contents = "";
        this.attributes = new HashMap<>();
        this.children = new ArrayList<>();
        this.entities = entities;
        this.lineNr = 0;
        if (fillBasicConversionTable) {
            this.entities.put("amp", new char[] {'&'});
            this.entities.put("quot", new char[] {'"'});
            this.entities.put("apos", new char[] {'\''});
            this.entities.put("lt", new char[] {'<'});
            this.entities.put("gt", new char[] {'>'});
        }
    }

    public void addChild(XMLElement child) {
        this.children.add(child);
    }

    public void setAttribute(String name, String value) {
        this.attributes.put(name, value);
    }

    public int countChildren() {
        return this.children.size();
    }

    public Enumeration<String> enumerateAttributeNames() {
        return Collections.enumeration(this.attributes.keySet());
    }

    /**
     * Enumerates the child elements.
     *
     * <dl>
     * <dt><b>Postconditions:</b></dt>
     * <dd>
     * <ul>
     * <li><code>result != null</code>
     * </ul>
     * </dd>
     * </dl>
     *
     * @see nanoxml.XMLElement#addChild(nanoxml.XMLElement) addChild(XMLElement)
     * @see nanoxml.XMLElement#countChildren()
     * @see nanoxml.XMLElement#children()
     * @see nanoxml.XMLElement#removeChild(nanoxml.XMLElement)
     *      removeChild(XMLElement)
     */
    public Enumeration<XMLElement> enumerateChildren() {
        return Collections.enumeration(children);
    }

    public List<XMLElement> children() {
        return new ArrayList<>(this.children);
    }

    public XMLElement firstChild() {
        return this.children.get(0);
    }

    public XMLElement child(String... names) {
        XMLElement x = this;
        XMLElement y = null;
        for (String name : names) {
            for (XMLElement child : x.children) {
                if (child.getName().equals(name)) {
                    y = child;
                }
            }
            if (y == null) {
                throw new NoSuchElementException("child not found with name: " + name);
            } else {
                x = y;
            }
        }
        return y;
    }

    public String content(String... names) {
        return child(names).getContent();
    }

    /**
     * Returns the PCDATA content of the object. If there is no such content,
     * <CODE>null</CODE> is returned.
     *
     * @see nanoxml.XMLElement#setContent(java.lang.String) setContent(String)
     */
    public String getContent() {
        return this.contents;
    }

    /**
     * Returns the line nr in the source data on which the element is found. This
     * method returns <code>0</code> there is no associated source data.
     */
    public int getLineNr() {
        return this.lineNr;
    }

    /**
     * Returns an attribute of the element. If the attribute doesn't exist,
     * <code>null</code> is returned.
     *
     * @param name The name of the attribute.
     */
    public String getAttribute(String name) {
        return this.getAttribute(name, null);
    }

    /**
     * Returns an attribute of the element. If the attribute doesn't exist,
     * <code>defaultValue</code> is returned.
     *
     * @param name         The name of the attribute.
     * @param defaultValue Key to use if the attribute is missing.
     */
    public String getAttribute(String name, String defaultValue) {
        return this.attributes.getOrDefault(name, defaultValue);
    }

    /**
     * Returns the name of the element.
     *
     * @see nanoxml.XMLElement#setName(java.lang.String) setName(String)
     */
    public String getName() {
        return this.name;
    }

    /**
     * Reads one XML element from a java.io.Reader and parses it.
     *
     * @param reader The reader from which to retrieve the XML data.
     *
     *               </dl>
     *               <dl>
     *               <dt><b>Preconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li><code>reader != null</code>
     *               <li><code>reader</code> is not closed
     *               </ul>
     *               </dd>
     *               </dl>
     *
     *               <dl>
     *               <dt><b>Postconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li>the state of the receiver is updated to reflect the XML
     *               element parsed from the reader
     *               <li>the reader points to the first character following the last
     *               '&gt;' character of the XML element
     *               </ul>
     *               </dd>
     *               </dl>
     *               <dl>
     *
     * @throws java.io.IOException       If an error occured while reading the
     *                                   input.
     * @throws nanoxml.XMLParseException If an error occured while parsing the read
     *                                   data.
     */
    public void parseFromReader(Reader reader) throws IOException, XMLParseException {
        this.parseFromReader(reader, /* startingLineNr */ 1);
    }

    /**
     * Reads one XML element from a java.io.Reader and parses it.
     *
     * @param reader         The reader from which to retrieve the XML data.
     * @param startingLineNr The line number of the first line in the data.
     *
     *                       </dl>
     *                       <dl>
     *                       <dt><b>Preconditions:</b></dt>
     *                       <dd>
     *                       <ul>
     *                       <li><code>reader != null</code>
     *                       <li><code>reader</code> is not closed
     *                       </ul>
     *                       </dd>
     *                       </dl>
     *
     *                       <dl>
     *                       <dt><b>Postconditions:</b></dt>
     *                       <dd>
     *                       <ul>
     *                       <li>the state of the receiver is updated to reflect the
     *                       XML element parsed from the reader
     *                       <li>the reader points to the first character following
     *                       the last '&gt;' character of the XML element
     *                       </ul>
     *                       </dd>
     *                       </dl>
     *                       <dl>
     *
     * @throws java.io.IOException       If an error occured while reading the
     *                                   input.
     * @throws nanoxml.XMLParseException If an error occured while parsing the read
     *                                   data.
     */
    public void parseFromReader(Reader reader, int startingLineNr)
            throws IOException, XMLParseException {
        this.name = null;
        this.contents = "";
        this.attributes = new HashMap<>();
        this.children = new ArrayList<>();
        this.charReadTooMuch = '\0';
        this.reader = reader;
        this.parserLineNr = startingLineNr;

        for (;;) {
            char ch = this.scanWhitespace();

            if (ch != '<') {
                throw this.createUnexpectedInputException("<");
            }

            ch = this.readChar();

            if ((ch == '!') || (ch == '?')) {
                this.skipSpecialTag(0);
            } else {
                this.unreadChar(ch);
                this.scanElement(this);
                return;
            }
        }
    }

    /**
     * Reads one XML element from a String and parses it.
     *
     * @param reader The reader from which to retrieve the XML data.
     *
     *               </dl>
     *               <dl>
     *               <dt><b>Preconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li><code>string != null</code>
     *               <li><code>string.length() &gt; 0</code>
     *               </ul>
     *               </dd>
     *               </dl>
     *
     *               <dl>
     *               <dt><b>Postconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li>the state of the receiver is updated to reflect the XML
     *               element parsed from the reader
     *               </ul>
     *               </dd>
     *               </dl>
     *               <dl>
     *
     * @throws nanoxml.XMLParseException If an error occured while parsing the
     *                                   string.
     */
    public void parseString(String string) throws XMLParseException {
        try {
            this.parseFromReader(new StringReader(string), /* startingLineNr */ 1);
        } catch (IOException e) {
            // Java exception handling suxx
        }
    }

    /**
     * Reads one XML element from a String and parses it.
     *
     * @param reader         The reader from which to retrieve the XML data.
     * @param offset         The first character in <code>string</code> to scan.
     * @param end            The character where to stop scanning. This character is
     *                       not scanned.
     * @param startingLineNr The line number of the first line in the data.
     *
     *                       </dl>
     *                       <dl>
     *                       <dt><b>Preconditions:</b></dt>
     *                       <dd>
     *                       <ul>
     *                       <li><code>string != null</code>
     *                       <li><code>end &lt;= string.length()</code>
     *                       <li><code>offset &lt; end</code>
     *                       <li><code>offset &gt;= 0</code>
     *                       </ul>
     *                       </dd>
     *                       </dl>
     *
     *                       <dl>
     *                       <dt><b>Postconditions:</b></dt>
     *                       <dd>
     *                       <ul>
     *                       <li>the state of the receiver is updated to reflect the
     *                       XML element parsed from the reader
     *                       </ul>
     *                       </dd>
     *                       </dl>
     *                       <dl>
     *
     * @throws nanoxml.XMLParseException If an error occured while parsing the
     *                                   string.
     */
    public void parseString(String string, int offset, int end, int startingLineNr)
            throws XMLParseException {
        string = string.substring(offset, end);
        try {
            this.parseFromReader(new StringReader(string), startingLineNr);
        } catch (IOException e) {
            // Java exception handling suxx
        }
    }

    /**
     * Reads one XML element from a char array and parses it.
     *
     * @param reader The reader from which to retrieve the XML data.
     * @param offset The first character in <code>string</code> to scan.
     * @param end    The character where to stop scanning. This character is not
     *               scanned.
     *
     *               </dl>
     *               <dl>
     *               <dt><b>Preconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li><code>input != null</code>
     *               <li><code>end &lt;= input.length</code>
     *               <li><code>offset &lt; end</code>
     *               <li><code>offset &gt;= 0</code>
     *               </ul>
     *               </dd>
     *               </dl>
     *
     *               <dl>
     *               <dt><b>Postconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li>the state of the receiver is updated to reflect the XML
     *               element parsed from the reader
     *               </ul>
     *               </dd>
     *               </dl>
     *               <dl>
     *
     * @throws nanoxml.XMLParseException If an error occured while parsing the
     *                                   string.
     */
    public void parseCharArray(char[] input, int offset, int end) throws XMLParseException {
        this.parseCharArray(input, offset, end, /* startingLineNr */ 1);
    }

    /**
     * Reads one XML element from a char array and parses it.
     *
     * @param reader         The reader from which to retrieve the XML data.
     * @param offset         The first character in <code>string</code> to scan.
     * @param end            The character where to stop scanning. This character is
     *                       not scanned.
     * @param startingLineNr The line number of the first line in the data.
     *
     *                       </dl>
     *                       <dl>
     *                       <dt><b>Preconditions:</b></dt>
     *                       <dd>
     *                       <ul>
     *                       <li><code>input != null</code>
     *                       <li><code>end &lt;= input.length</code>
     *                       <li><code>offset &lt; end</code>
     *                       <li><code>offset &gt;= 0</code>
     *                       </ul>
     *                       </dd>
     *                       </dl>
     *
     *                       <dl>
     *                       <dt><b>Postconditions:</b></dt>
     *                       <dd>
     *                       <ul>
     *                       <li>the state of the receiver is updated to reflect the
     *                       XML element parsed from the reader
     *                       </ul>
     *                       </dd>
     *                       </dl>
     *                       <dl>
     *
     * @throws nanoxml.XMLParseException If an error occured while parsing the
     *                                   string.
     */
    public void parseCharArray(char[] input, int offset, int end, int startingLineNr)
            throws XMLParseException {
        try {
            Reader reader = new CharArrayReader(input, offset, end);
            this.parseFromReader(reader, startingLineNr);
        } catch (IOException e) {
            // This exception will never happen.
        }
    }

    /**
     * Removes a child element.
     *
     * @param child The child element to remove.
     *
     *              </dl>
     *              <dl>
     *              <dt><b>Preconditions:</b></dt>
     *              <dd>
     *              <ul>
     *              <li><code>child != null</code>
     *              <li><code>child</code> is a child element of the receiver
     *              </ul>
     *              </dd>
     *              </dl>
     *
     *              <dl>
     *              <dt><b>Postconditions:</b></dt>
     *              <dd>
     *              <ul>
     *              <li>countChildren() => old.countChildren() - 1
     *              <li>enumerateChildren() => old.enumerateChildren() - child
     *              <li>getChildren() => old.enumerateChildren() - child
     *              </ul>
     *              </dd>
     *              </dl>
     *              <dl>
     *
     * @see nanoxml.XMLElement#addChild(nanoxml.XMLElement) addChild(XMLElement)
     * @see nanoxml.XMLElement#countChildren()
     * @see nanoxml.XMLElement#enumerateChildren()
     * @see nanoxml.XMLElement#children()
     */
    public void removeChild(XMLElement child) {
        this.children.remove(child);
    }

    /**
     * Removes an attribute.
     *
     * @param name The name of the attribute.
     *
     *             </dl>
     *             <dl>
     *             <dt><b>Preconditions:</b></dt>
     *             <dd>
     *             <ul>
     *             <li><code>name != null</code>
     *             <li><code>name</code> is a valid XML identifier
     *             </ul>
     *             </dd>
     *             </dl>
     *
     *             <dl>
     *             <dt><b>Postconditions:</b></dt>
     *             <dd>
     *             <ul>
     *             <li>enumerateAttributeNames() => old.enumerateAttributeNames() -
     *             name
     *             <li>getAttribute(name) => <code>null</code>
     *             </ul>
     *             </dd>
     *             </dl>
     *             <dl>
     *
     * @see nanoxml.XMLElement#enumerateAttributeNames()
     * @see nanoxml.XMLElement#setDoubleAttribute(java.lang.String, double)
     *      setDoubleAttribute(String, double)
     * @see nanoxml.XMLElement#setIntAttribute(java.lang.String, int)
     *      setIntAttribute(String, int)
     * @see nanoxml.XMLElement#setAttribute(java.lang.String, java.lang.Object)
     *      setAttribute(String, Object)
     * @see nanoxml.XMLElement#getAttribute(java.lang.String) getAttribute(String)
     * @see nanoxml.XMLElement#getAttribute(java.lang.String, java.lang.Object)
     *      getAttribute(String, Object)
     * @see nanoxml.XMLElement#getAttribute(java.lang.String, java.util.Hashtable,
     *      java.lang.String, boolean) getAttribute(String, Hashtable, String,
     *      boolean)
     * @see nanoxml.XMLElement#getStringAttribute(java.lang.String)
     *      getStringAttribute(String)
     * @see nanoxml.XMLElement#getStringAttribute(java.lang.String,
     *      java.lang.String) getStringAttribute(String, String)
     * @see nanoxml.XMLElement#getStringAttribute(java.lang.String,
     *      java.util.Hashtable, java.lang.String, boolean)
     *      getStringAttribute(String, Hashtable, String, boolean)
     * @see nanoxml.XMLElement#getIntAttribute(java.lang.String)
     *      getIntAttribute(String)
     * @see nanoxml.XMLElement#getIntAttribute(java.lang.String, int)
     *      getIntAttribute(String, int)
     * @see nanoxml.XMLElement#getDoubleAttribute(java.lang.String)
     *      getDoubleAttribute(String)
     * @see nanoxml.XMLElement#getDoubleAttribute(java.lang.String, double)
     *      getDoubleAttribute(String, double)
     * @see nanoxml.XMLElement#getDoubleAttribute(java.lang.String,
     *      java.util.Hashtable, java.lang.String, boolean)
     *      getDoubleAttribute(String, Hashtable, String, boolean)
     * @see nanoxml.XMLElement#getBooleanAttribute(java.lang.String,
     *      java.lang.String, java.lang.String, boolean) getBooleanAttribute(String,
     *      String, String, boolean)
     */
    public void removeAttribute(String name) {
        this.attributes.remove(name);
    }

    /**
     * Creates a new similar XML element.
     * <P>
     * You should override this method when subclassing XMLElement.
     */
    protected XMLElement createAnotherElement() {
        return new XMLElement(this.entities, this.ignoreWhitespace, false);
    }

    /**
     * Changes the content string.
     *
     * @param content The new content string.
     */
    public void setContent(String content) {
        this.contents = content;
    }

    /**
     * Changes the name of the element.
     *
     * @param name The new name.
     *
     *             </dl>
     *             <dl>
     *             <dt><b>Preconditions:</b></dt>
     *             <dd>
     *             <ul>
     *             <li><code>name != null</code>
     *             <li><code>name</code> is a valid XML identifier
     *             </ul>
     *             </dd>
     *             </dl>
     *
     * @see nanoxml.XMLElement#getName()
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Writes the XML element to a string.
     *
     * @see nanoxml.XMLElement#write(java.io.Writer) write(Writer)
     */
    public String toString() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out)) {
            this.write(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new String(out.toByteArray());
    }

    /**
     * Writes the XML element to a writer.
     *
     * @param writer The writer to write the XML data to.
     *
     *               </dl>
     *               <dl>
     *               <dt><b>Preconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li><code>writer != null</code>
     *               <li><code>writer</code> is not closed
     *               </ul>
     *               </dd>
     *               </dl>
     *
     * @throws java.io.IOException If the data could not be written to the writer.
     *
     * @see nanoxml.XMLElement#toString()
     */
    public void write(Writer writer) throws IOException {
        if (this.name == null) {
            this.writeEncoded(writer, this.contents);
            return;
        }
        writer.write('<');
        writer.write(this.name);
        if (!this.attributes.isEmpty()) {
            Enumeration<String> en = Collections.enumeration(this.attributes.keySet());
            while (en.hasMoreElements()) {
                writer.write(' ');
                String key = (String) en.nextElement();
                String value = (String) this.attributes.get(key);
                writer.write(key);
                writer.write('=');
                writer.write('"');
                this.writeEncoded(writer, value);
                writer.write('"');
            }
        }
        if ((this.contents != null) && (this.contents.length() > 0)) {
            writer.write('>');
            this.writeEncoded(writer, this.contents);
            writer.write('<');
            writer.write('/');
            writer.write(this.name);
            writer.write('>');
        } else if (this.children.isEmpty()) {
            writer.write('/');
            writer.write('>');
        } else {
            writer.write('>');
            Enumeration<?> en = this.enumerateChildren();
            while (en.hasMoreElements()) {
                XMLElement child = (XMLElement) en.nextElement();
                child.write(writer);
            }
            writer.write('<');
            writer.write('/');
            writer.write(this.name);
            writer.write('>');
        }
    }

    /**
     * Writes a string encoded to a writer.
     *
     * @param writer The writer to write the XML data to.
     * @param str    The string to write encoded.
     *
     *               </dl>
     *               <dl>
     *               <dt><b>Preconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li><code>writer != null</code>
     *               <li><code>writer</code> is not closed
     *               <li><code>str != null</code>
     *               </ul>
     *               </dd>
     *               </dl>
     */
    protected void writeEncoded(Writer writer, String str) throws IOException {
        for (int i = 0; i < str.length(); i += 1) {
            char ch = str.charAt(i);
            switch (ch) {
            case '<':
                writer.write('&');
                writer.write('l');
                writer.write('t');
                writer.write(';');
                break;
            case '>':
                writer.write('&');
                writer.write('g');
                writer.write('t');
                writer.write(';');
                break;
            case '&':
                writer.write('&');
                writer.write('a');
                writer.write('m');
                writer.write('p');
                writer.write(';');
                break;
            case '"':
                writer.write('&');
                writer.write('q');
                writer.write('u');
                writer.write('o');
                writer.write('t');
                writer.write(';');
                break;
            case '\'':
                writer.write('&');
                writer.write('a');
                writer.write('p');
                writer.write('o');
                writer.write('s');
                writer.write(';');
                break;
            default:
                int unicode = (int) ch;
                if ((unicode < 32) || (unicode > 126)) {
                    writer.write('&');
                    writer.write('#');
                    writer.write('x');
                    writer.write(Integer.toString(unicode, 16));
                    writer.write(';');
                } else {
                    writer.write(ch);
                }
            }
        }
    }

    /**
     * Scans an identifier from the current reader. The scanned identifier is
     * appended to <code>result</code>.
     *
     * @param result The buffer in which the scanned identifier will be put.
     *
     *               </dl>
     *               <dl>
     *               <dt><b>Preconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li><code>result != null</code>
     *               <li>The next character read from the reader is a valid first
     *               character of an XML identifier.
     *               </ul>
     *               </dd>
     *               </dl>
     *
     *               <dl>
     *               <dt><b>Postconditions:</b></dt>
     *               <dd>
     *               <ul>
     *               <li>The next character read from the reader won't be an
     *               identifier character.
     *               </ul>
     *               </dd>
     *               </dl>
     *               <dl>
     */
    protected void scanIdentifier(StringBuilder result) throws IOException {
        for (;;) {
            char ch = this.readChar();
            if (((ch < 'A') || (ch > 'Z')) && ((ch < 'a') || (ch > 'z'))
                    && ((ch < '0') || (ch > '9')) && (ch != '_') && (ch != '.') && (ch != ':')
                    && (ch != '-') && (ch <= '\u007E')) {
                this.unreadChar(ch);
                return;
            }
            result.append(ch);
        }
    }

    /**
     * This method scans an identifier from the current reader.
     *
     * @return the next character following the whitespace.
     */
    protected char scanWhitespace() throws IOException {
        for (;;) {
            char ch = this.readChar();
            switch (ch) {
            case ' ':
            case '\t':
            case '\n':
            case '\r':
                break;
            default:
                return ch;
            }
        }
    }

    /**
     * This method scans an identifier from the current reader. The scanned
     * whitespace is appended to <code>result</code>.
     *
     * @return the next character following the whitespace.
     *
     *         </dl>
     *         <dl>
     *         <dt><b>Preconditions:</b></dt>
     *         <dd>
     *         <ul>
     *         <li><code>result != null</code>
     *         </ul>
     *         </dd>
     *         </dl>
     */
    protected char scanWhitespace(StringBuilder result) throws IOException {
        for (;;) {
            char ch = this.readChar();
            switch (ch) {
            case ' ':
            case '\t':
            case '\n':
                result.append(ch);
            case '\r':
                break;
            default:
                return ch;
            }
        }
    }

    /**
     * This method scans a delimited string from the current reader. The scanned
     * string without delimiters is appended to <code>string</code>.
     *
     * </dl>
     * <dl>
     * <dt><b>Preconditions:</b></dt>
     * <dd>
     * <ul>
     * <li><code>string != null</code>
     * <li>the next char read is the string delimiter
     * </ul>
     * </dd>
     * </dl>
     */
    protected void scanString(StringBuilder string) throws IOException {
        char delimiter = this.readChar();
        if ((delimiter != '\'') && (delimiter != '"')) {
            throw this.createUnexpectedInputException("' or \"");
        }
        for (;;) {
            char ch = this.readChar();
            if (ch == delimiter) {
                return;
            } else if (ch == '&') {
                this.resolveEntity(string);
            } else {
                string.append(ch);
            }
        }
    }

    /**
     * Scans a #PCDATA element. CDATA sections and entities are resolved. The next
     * &lt; char is skipped. The scanned data is appended to <code>data</code>.
     *
     * </dl>
     * <dl>
     * <dt><b>Preconditions:</b></dt>
     * <dd>
     * <ul>
     * <li><code>data != null</code>
     * </ul>
     * </dd>
     * </dl>
     */
    protected void scanPCData(StringBuilder data) throws IOException {
        for (;;) {
            char ch = this.readChar();
            if (ch == '<') {
                ch = this.readChar();
                if (ch == '!') {
                    this.checkCDATA(data);
                } else {
                    this.unreadChar(ch);
                    return;
                }
            } else if (ch == '&') {
                this.resolveEntity(data);
            } else {
                data.append(ch);
            }
        }
    }

    /**
     * Scans a special tag and if the tag is a CDATA section, append its content to
     * <code>buf</code>.
     *
     * </dl>
     * <dl>
     * <dt><b>Preconditions:</b></dt>
     * <dd>
     * <ul>
     * <li><code>buf != null</code>
     * <li>The first &lt; has already been read.
     * </ul>
     * </dd>
     * </dl>
     */
    protected boolean checkCDATA(StringBuilder buf) throws IOException {
        char ch = this.readChar();
        if (ch != '[') {
            this.unreadChar(ch);
            this.skipSpecialTag(0);
            return false;
        } else if (!this.checkLiteral("CDATA[")) {
            this.skipSpecialTag(1); // one [ has already been read
            return false;
        } else {
            int delimiterCharsSkipped = 0;
            while (delimiterCharsSkipped < 3) {
                ch = this.readChar();
                switch (ch) {
                case ']':
                    if (delimiterCharsSkipped < 2) {
                        delimiterCharsSkipped += 1;
                    } else {
                        buf.append(']');
                        buf.append(']');
                        delimiterCharsSkipped = 0;
                    }
                    break;
                case '>':
                    if (delimiterCharsSkipped < 2) {
                        for (int i = 0; i < delimiterCharsSkipped; i++) {
                            buf.append(']');
                        }
                        delimiterCharsSkipped = 0;
                        buf.append('>');
                    } else {
                        delimiterCharsSkipped = 3;
                    }
                    break;
                default:
                    for (int i = 0; i < delimiterCharsSkipped; i += 1) {
                        buf.append(']');
                    }
                    buf.append(ch);
                    delimiterCharsSkipped = 0;
                }
            }
            return true;
        }
    }

    /**
     * Skips a comment.
     *
     * </dl>
     * <dl>
     * <dt><b>Preconditions:</b></dt>
     * <dd>
     * <ul>
     * <li>The first &lt;!-- has already been read.
     * </ul>
     * </dd>
     * </dl>
     */
    protected void skipComment() throws IOException {
        int dashesToRead = 2;
        while (dashesToRead > 0) {
            char ch = this.readChar();
            if (ch == '-') {
                dashesToRead -= 1;
            } else {
                dashesToRead = 2;
            }
        }
        if (this.readChar() != '>') {
            throw this.createUnexpectedInputException(">");
        }
    }

    /**
     * Skips a special tag or comment.
     *
     * @param bracketLevel The number of open square brackets ([) that have already
     *                     been read.
     *
     *                     </dl>
     *                     <dl>
     *                     <dt><b>Preconditions:</b></dt>
     *                     <dd>
     *                     <ul>
     *                     <li>The first &lt;! has already been read.
     *                     <li><code>bracketLevel >= 0</code>
     *                     </ul>
     *                     </dd>
     *                     </dl>
     */
    protected void skipSpecialTag(int bracketLevel) throws IOException {
        int tagLevel = 1; // <
        char stringDelimiter = '\0';
        if (bracketLevel == 0) {
            char ch = this.readChar();
            if (ch == '[') {
                bracketLevel += 1;
            } else if (ch == '-') {
                ch = this.readChar();
                if (ch == '[') {
                    bracketLevel += 1;
                } else if (ch == ']') {
                    bracketLevel -= 1;
                } else if (ch == '-') {
                    this.skipComment();
                    return;
                }
            }
        }
        while (tagLevel > 0) {
            char ch = this.readChar();
            if (stringDelimiter == '\0') {
                if ((ch == '"') || (ch == '\'')) {
                    stringDelimiter = ch;
                } else if (bracketLevel <= 0) {
                    if (ch == '<') {
                        tagLevel += 1;
                    } else if (ch == '>') {
                        tagLevel -= 1;
                    }
                }
                if (ch == '[') {
                    bracketLevel += 1;
                } else if (ch == ']') {
                    bracketLevel -= 1;
                }
            } else {
                if (ch == stringDelimiter) {
                    stringDelimiter = '\0';
                }
            }
        }
    }

    /**
     * Scans the data for literal text. Scanning stops when a character does not
     * match or after the complete text has been checked, whichever comes first.
     *
     * @param literal the literal to check.
     *
     *                </dl>
     *                <dl>
     *                <dt><b>Preconditions:</b></dt>
     *                <dd>
     *                <ul>
     *                <li><code>literal != null</code>
     *                </ul>
     *                </dd>
     *                </dl>
     */
    protected boolean checkLiteral(String literal) throws IOException {
        int length = literal.length();
        for (int i = 0; i < length; i += 1) {
            if (this.readChar() != literal.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a character from a reader.
     */
    protected char readChar() throws IOException {
        if (this.charReadTooMuch != '\0') {
            char ch = this.charReadTooMuch;
            this.charReadTooMuch = '\0';
            return ch;
        } else {
            int i = this.reader.read();
            if (i < 0) {
                throw this.createExceptionUnexpectedEndOfData();
            } else if (i == 10) {
                this.parserLineNr += 1;
                return '\n';
            } else {
                return (char) i;
            }
        }
    }

    /**
     * Scans an XML element.
     *
     * @param elt The element that will contain the result.
     *
     *            </dl>
     *            <dl>
     *            <dt><b>Preconditions:</b></dt>
     *            <dd>
     *            <ul>
     *            <li>The first &lt; has already been read.
     *            <li><code>elt != null</code>
     *            </ul>
     *            </dd>
     *            </dl>
     */
    protected void scanElement(XMLElement elt) throws IOException {
        StringBuilder buf = new StringBuilder();
        this.scanIdentifier(buf);
        String name = buf.toString();
        elt.setName(name);
        char ch = this.scanWhitespace();
        while ((ch != '>') && (ch != '/')) {
            buf.setLength(0);
            this.unreadChar(ch);
            this.scanIdentifier(buf);
            String key = buf.toString();
            ch = this.scanWhitespace();
            if (ch != '=') {
                throw this.createUnexpectedInputException("=");
            }
            this.unreadChar(this.scanWhitespace());
            buf.setLength(0);
            this.scanString(buf);
            elt.setAttribute(key, buf.toString());
            ch = this.scanWhitespace();
        }
        if (ch == '/') {
            ch = this.readChar();
            if (ch != '>') {
                throw this.createUnexpectedInputException(">");
            }
            return;
        }
        buf.setLength(0);
        ch = this.scanWhitespace(buf);
        if (ch != '<') {
            this.unreadChar(ch);
            this.scanPCData(buf);
        } else {
            for (;;) {
                ch = this.readChar();
                if (ch == '!') {
                    if (this.checkCDATA(buf)) {
                        this.scanPCData(buf);
                        break;
                    } else {
                        ch = this.scanWhitespace(buf);
                        if (ch != '<') {
                            this.unreadChar(ch);
                            this.scanPCData(buf);
                            break;
                        }
                    }
                } else {
                    if ((ch != '/') || this.ignoreWhitespace) {
                        buf.setLength(0);
                    }
                    if (ch == '/') {
                        this.unreadChar(ch);
                    }
                    break;
                }
            }
        }
        if (buf.length() == 0) {
            while (ch != '/') {
                if (ch == '!') {
                    ch = this.readChar();
                    if (ch != '-') {
                        throw this.createUnexpectedInputException("Comment or Element");
                    }
                    ch = this.readChar();
                    if (ch != '-') {
                        throw this.createUnexpectedInputException("Comment or Element");
                    }
                    this.skipComment();
                } else {
                    this.unreadChar(ch);
                    XMLElement child = this.createAnotherElement();
                    this.scanElement(child);
                    elt.addChild(child);
                }
                ch = this.scanWhitespace();
                if (ch != '<') {
                    throw this.createUnexpectedInputException("<");
                }
                ch = this.readChar();
            }
            this.unreadChar(ch);
        } else {
            if (this.ignoreWhitespace) {
                elt.setContent(buf.toString().trim());
            } else {
                elt.setContent(buf.toString());
            }
        }
        ch = this.readChar();
        if (ch != '/') {
            throw this.createUnexpectedInputException("/");
        }
        this.unreadChar(this.scanWhitespace());
        if (!this.checkLiteral(name)) {
            throw this.createUnexpectedInputException(name);
        }
        if (this.scanWhitespace() != '>') {
            throw this.createUnexpectedInputException(">");
        }
    }

    /**
     * Resolves an entity. The name of the entity is read from the reader. The value
     * of the entity is appended to <code>buf</code>.
     *
     * @param buf Where to put the entity value.
     *
     *            </dl>
     *            <dl>
     *            <dt><b>Preconditions:</b></dt>
     *            <dd>
     *            <ul>
     *            <li>The first &amp; has already been read.
     *            <li><code>buf != null</code>
     *            </ul>
     *            </dd>
     *            </dl>
     */
    protected void resolveEntity(StringBuilder buf) throws IOException {
        char ch = '\0';
        StringBuilder keyBuf = new StringBuilder();
        for (;;) {
            ch = this.readChar();
            if (ch == ';') {
                break;
            }
            keyBuf.append(ch);
        }
        String key = keyBuf.toString();
        if (key.charAt(0) == '#') {
            try {
                if (key.charAt(1) == 'x') {
                    ch = (char) Integer.parseInt(key.substring(2), 16);
                } else {
                    ch = (char) Integer.parseInt(key.substring(1), 10);
                }
            } catch (NumberFormatException e) {
                throw this.createExceptionUnknownEntity(key);
            }
            buf.append(ch);
        } else {
            char[] value = (char[]) this.entities.get(key);
            if (value == null) {
                throw this.createExceptionUnknownEntity(key);
            }
            buf.append(value);
        }
    }

    /**
     * Pushes a character back to the read-back buffer.
     *
     * @param ch The character to push back.
     *
     *           </dl>
     *           <dl>
     *           <dt><b>Preconditions:</b></dt>
     *           <dd>
     *           <ul>
     *           <li>The read-back buffer is empty.
     *           <li><code>ch != '\0'</code>
     *           </ul>
     *           </dd>
     *           </dl>
     */
    protected void unreadChar(char ch) {
        this.charReadTooMuch = ch;
    }

    /**
     * Creates a parse exception for when an invalid valueset is given to a method.
     *
     * @param name The name of the entity.
     *
     *             </dl>
     *             <dl>
     *             <dt><b>Preconditions:</b></dt>
     *             <dd>
     *             <ul>
     *             <li><code>name != null</code>
     *             </ul>
     *             </dd>
     *             </dl>
     */
    protected XMLParseException invalidValueSet(String name) {
        String msg = "Invalid value set (entity name = \"" + name + "\")";
        return new XMLParseException(this.getName(), this.parserLineNr, msg);
    }

    /**
     * Creates a parse exception for when an invalid value is given to a method.
     *
     * @param name  The name of the entity.
     * @param value The value of the entity.
     *
     *              </dl>
     *              <dl>
     *              <dt><b>Preconditions:</b></dt>
     *              <dd>
     *              <ul>
     *              <li><code>name != null</code>
     *              <li><code>value != null</code>
     *              </ul>
     *              </dd>
     *              </dl>
     */
    protected XMLParseException invalidValue(String name, String value) {
        String msg = "Attribute \"" + name + "\" does not contain a valid " + "value (\"" + value
                + "\")";
        return new XMLParseException(this.getName(), this.parserLineNr, msg);
    }

    /**
     * Creates a parse exception for when the end of the data input has been
     * reached.
     */
    protected XMLParseException createExceptionUnexpectedEndOfData() {
        String msg = "Unexpected end of data reached";
        return new XMLParseException(this.getName(), this.parserLineNr, msg);
    }

    /**
     * Creates a parse exception for when a syntax error occured.
     *
     * @param context The context in which the error occured.
     *
     *                </dl>
     *                <dl>
     *                <dt><b>Preconditions:</b></dt>
     *                <dd>
     *                <ul>
     *                <li><code>context != null</code>
     *                <li><code>context.length() &gt; 0</code>
     *                </ul>
     *                </dd>
     *                </dl>
     */
    protected XMLParseException syntaxError(String context) {
        String msg = "Syntax error while parsing " + context;
        return new XMLParseException(this.getName(), this.parserLineNr, msg);
    }

    /**
     * Creates a parse exception for when the next character read is not the
     * character that was expected.
     *
     * @param charSet The set of characters (in human readable form) that was
     *                expected.
     *
     *                </dl>
     *                <dl>
     *                <dt><b>Preconditions:</b></dt>
     *                <dd>
     *                <ul>
     *                <li><code>charSet != null</code>
     *                <li><code>charSet.length() &gt; 0</code>
     *                </ul>
     *                </dd>
     *                </dl>
     */
    protected XMLParseException createUnexpectedInputException(String charSet) {
        String msg = "Expected: " + charSet;
        return new XMLParseException(this.getName(), this.parserLineNr, msg);
    }

    /**
     * Creates a parse exception for when an entity could not be resolved.
     *
     * @param name The name of the entity.
     *
     *             </dl>
     *             <dl>
     *             <dt><b>Preconditions:</b></dt>
     *             <dd>
     *             <ul>
     *             <li><code>name != null</code>
     *             <li><code>name.length() &gt; 0</code>
     *             </ul>
     *             </dd>
     *             </dl>
     */
    protected XMLParseException createExceptionUnknownEntity(String name) {
        String msg = "Unknown or invalid entity: &" + name + ";";
        return new XMLParseException(this.getName(), this.parserLineNr, msg);
    }

}
