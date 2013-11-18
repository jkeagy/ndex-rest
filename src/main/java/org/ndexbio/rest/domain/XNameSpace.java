package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
public interface XNameSpace extends VertexFrame {
    @Property("jdex_id")
    public String getJdexId();

    @Property("jdex_id")
    public void setJdexId(String jdexId);

    @Property("prefix")
    public String getPrefix();

    @Property("prefix")
    public void setPrefix(String prefix);

    @Property("uri")
    public void setUri(String uri);

    @Property("uri")
    public String getUri();
}
