package com.stephanofer.networkboosters.localization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class MessageArguments {

    private MessageArguments() {
    }

    public static TagResolver text(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver component(String key, Component component) {
        return Placeholder.component(key, component);
    }
}
