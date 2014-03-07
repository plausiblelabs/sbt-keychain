sbt-keychain
-----------

sbt-keychain provides support for fetching repository credentials from the system keychain,
rather than including them in plaintext in a local credentials file.

The plugin currently provides basic support for credential lookup via [git credential helpers](http://git-scm.com/docs/gitcredentials.html),
including `git-credential-osxkeychain` for Mac OS X, and [git-credential-winstore](http://gitcredentialstore.codeplex.com/)
on Windows.

In the future, we'll investigate adding direct support for querying the system keychain APIs, including:

- [Keychain Services](https://developer.apple.com/library/mac/documentation/security/Reference/keychainservices/Reference/reference.html) for Mac OS X.
- [Credentials Management](http://msdn.microsoft.com/en-us/library/windows/desktop/aa374789(v=vs.85).aspx) on Windows.
- [Gnome Keyring](https://wiki.gnome.org/action/show/Projects/GnomeKeyring?action=show&redirect=GnomeKeyring) on Linux.

Contributions of keychain storage backends are most welcome.

Installation and Configuration
-----------

Add the following to your `~/.sbt/0.13/plugins/gpg.sbt` file (you may need to create the directory):

    // Required until we can submit the plugin to Maven Central
    resolvers += "Plausible OSS" at "https://opensource.plausible.coop/nexus/content/repositories/public"

    addSbtPlugin("coop.plausible" %% "sbt-keychain" % "1.0")

Configure the set of accounts to be fetched from the keychain in `~/.sbt/0.13/global.sbt`:

    import coop.plausible.sbt.keychain.{KeychainAccount, KeychainPlugin}

    KeychainPlugin.keychainSettings

    keychainAccounts ++= Seq(
        KeychainAccount("Sonatype Nexus Repository Manager", "https://maven.example.org", Some("my-username"))
    )

    credentials <++= KeychainPlugin.keychainCredentials

Credentials for the listed accounts will be automatically fetched from the keychain by `KeychainPlugin.keychainCredentials`.
If `username` is left unspecified (eg, `None`), the keychain plugin will try to derive the account name from the keychain.