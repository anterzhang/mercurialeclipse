/**
 * Copyright 2005,2009 Ivan SZKIBA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ini4j;

public class Config implements Cloneable
{
    public static final String KEY_PREFIX = "org.ini4j.config.";
    public static final String PROP_EMPTY_OPTION = "emptyOption";
    public static final String PROP_GLOBAL_SECTION = "globalSection";
    public static final String PROP_GLOBAL_SECTION_NAME = "globalSectionName";
    public static final String PROP_INCLUDE = "include";
    public static final String PROP_LOWER_CASE_OPTION = "lowerCaseOption";
    public static final String PROP_LOWER_CASE_SECTION = "lowerCaseSection";
    public static final String PROP_MULTI_OPTION = "multiOption";
    public static final String PROP_MULTI_SECTION = "multiSection";
    public static final String PROP_STRICT_OPERATOR = "strictOperator";
    public static final String PROP_UNNAMED_SECTION = "unnamedSection";
    public static final String PROP_ESCAPE = "escape";
    public static final boolean DEFAULT_EMPTY_OPTION = false;
    public static final boolean DEFAULT_GLOBAL_SECTION = false;
    public static final String DEFAULT_GLOBAL_SECTION_NAME = "?";
    public static final boolean DEFAULT_INCLUDE = false;
    public static final boolean DEFAULT_LOWER_CASE_OPTION = false;
    public static final boolean DEFAULT_LOWER_CASE_SECTION = false;
    public static final boolean DEFAULT_MULTI_OPTION = false;
    public static final boolean DEFAULT_MULTI_SECTION = false;
    public static final boolean DEFAULT_STRICT_OPERATOR = false;
    public static final boolean DEFAULT_UNNAMED_SECTION = false;
    public static final boolean DEFAULT_ESCAPE = true;
    private static final Config _global = new Config();
    private boolean _emptyOption;
    private boolean _escape;
    private boolean _globalSection;
    private String _globalSectionName;
    private boolean _include;
    private boolean _lowerCaseOption;
    private boolean _lowerCaseSection;
    private boolean _multiOption;
    private boolean _multiSection;
    private boolean _strictOperator;
    private boolean _unnamedSection;

    public Config()
    {
        reset();
    }

    public static Config getGlobal()
    {
        return _global;
    }

    public boolean isEscape()
    {
        return _escape;
    }

    public boolean isInclude()
    {
        return _include;
    }

    public void setEmptyOption(boolean value)
    {
        _emptyOption = value;
    }

    public void setEscape(boolean value)
    {
        _escape = value;
    }

    public void setGlobalSection(boolean value)
    {
        _globalSection = value;
    }

    public String getGlobalSectionName()
    {
        return _globalSectionName;
    }

    public void setGlobalSectionName(String value)
    {
        _globalSectionName = value;
    }

    public void setInclude(boolean value)
    {
        _include = value;
    }

    public void setLowerCaseOption(boolean value)
    {
        _lowerCaseOption = value;
    }

    public void setLowerCaseSection(boolean value)
    {
        _lowerCaseSection = value;
    }

    public void setMultiOption(boolean value)
    {
        _multiOption = value;
    }

    public void setMultiSection(boolean value)
    {
        _multiSection = value;
    }

    public boolean isEmptyOption()
    {
        return _emptyOption;
    }

    public boolean isGlobalSection()
    {
        return _globalSection;
    }

    public boolean isLowerCaseOption()
    {
        return _lowerCaseOption;
    }

    public boolean isLowerCaseSection()
    {
        return _lowerCaseSection;
    }

    public boolean isMultiOption()
    {
        return _multiOption;
    }

    public boolean isMultiSection()
    {
        return _multiSection;
    }

    public boolean isUnnamedSection()
    {
        return _unnamedSection;
    }

    public boolean isStrictOperator()
    {
        return _strictOperator;
    }

    public void setStrictOperator(boolean value)
    {
        _strictOperator = value;
    }

    public void setUnnamedSection(boolean value)
    {
        _unnamedSection = value;
    }

    @Override
    public Config clone()
    {
        try
        {
            return (Config) super.clone();
        }
        catch (CloneNotSupportedException x)
        {
            throw new AssertionError();
        }
    }

    public final void reset()
    {
        _emptyOption = getBoolean(PROP_EMPTY_OPTION, DEFAULT_EMPTY_OPTION);
        _globalSection = getBoolean(PROP_GLOBAL_SECTION, DEFAULT_GLOBAL_SECTION);
        _globalSectionName = getString(PROP_GLOBAL_SECTION_NAME, DEFAULT_GLOBAL_SECTION_NAME);
        _include = getBoolean(PROP_INCLUDE, DEFAULT_INCLUDE);
        _lowerCaseOption = getBoolean(PROP_LOWER_CASE_OPTION, DEFAULT_LOWER_CASE_OPTION);
        _lowerCaseSection = getBoolean(PROP_LOWER_CASE_SECTION, DEFAULT_LOWER_CASE_SECTION);
        _multiOption = getBoolean(PROP_MULTI_OPTION, DEFAULT_MULTI_OPTION);
        _multiSection = getBoolean(PROP_MULTI_SECTION, DEFAULT_MULTI_SECTION);
        _strictOperator = getBoolean(PROP_STRICT_OPERATOR, DEFAULT_STRICT_OPERATOR);
        _unnamedSection = getBoolean(PROP_UNNAMED_SECTION, DEFAULT_UNNAMED_SECTION);
        _escape = getBoolean(PROP_ESCAPE, DEFAULT_ESCAPE);
    }

    private boolean getBoolean(String name, boolean defaultValue)
    {
        String key = KEY_PREFIX + name;

        return System.getProperties().containsKey(key) ? Boolean.getBoolean(key) : defaultValue;
    }

    private String getString(String name, String defaultValue)
    {
        String key = KEY_PREFIX + name;

        return System.getProperties().containsKey(key) ? System.getProperty(key) : defaultValue;
    }
}
