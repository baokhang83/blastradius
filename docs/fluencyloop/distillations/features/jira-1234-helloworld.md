# Hello-world Maven goal

Before this feature, Blastradius exposed its test-selection goal but had no minimal command for
verifying that the Maven plugin resolved and ran. It now provides `blastradius:hello`, which writes
`Hello, world!` through Maven's standard logger without configuring or affecting test selection.
