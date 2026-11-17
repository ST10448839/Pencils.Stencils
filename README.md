# ✏️ Pencils & Stencils

---

**📌 Overview**

A Kotlin-based Android shopping app prototype for browsing creative supplies. Users can explore the collection, view product details, manage a shopping bag, and enter delivery information in a demonstration checkout. Firebase Authentication supports email/password registration and login, with Google sign-in available for an SSO feature.

---

**👤 Group Members**

- Keenan Neil Scully – ST10438180
- Raihaan Rajah – ST10448839
- Muhammad Aashiq Petersen – ST10435507
- Teagon Patrick Vermeulen – ST10437198

---

**🎥 Link to Demonstration Video: (YouTube)**

[https://youtu.be/D-9ucvWjdZ4?si=Hj_z8up7dFnRue6P](https://youtu.be/D-9ucvWjdZ4?si=Hj_z8up7dFnRue6P)

---

**🚀 Features**

- Register and log in with email and password using Firebase Authentication
- Separate Google sign-in option.
- Home screen with shortcuts to the product collection
- Browse products by category
- Open product details with the name, description, price, and add-to-bag action
- See suggested products from the collection
- Add products to the shopping bag and receive a branded confirmation popup
- Change item quantities or remove items from the bag
- Review subtotal, delivery, VAT, and order total
- Enter and validate delivery details in the demonstration checkout
- Account and shopping settings, including password actions and product display preferences
- Side navigation to the main app sections
- In-app Help & User Guide focused on using the app
- Ten sample art-supply products with matching product images

---

**🎨 Design Considerations**

- Dark backgrounds and pale text keep the interface consistent with the supplied Pencils & Stencils design.
- Orange marks primary actions, mint highlights supporting information, and blue shadow accents add emphasis to buttons and cards.
- Clear headings, product names, prices, and short labels help users scan screens quickly.
- Touch-friendly controls and Android system insets keep content visible around the status bar and navigation area.
- Form validation and helpful error messages guide users when required or invalid information is entered.
- Checkout is a prototype: it does not charge a card or store or send payment details. Do not enter real card information.

---

**🛠️ Technologies Used**

- Kotlin
- Android Studio
- Android Gradle build system
- Firebase Authentication for account sign-in
- Android Credential Manager for Google sign-in
- GitHub for source-code hosting and version history
- GitHub Actions for automated build and test checks when the repository workflow is configured

---

**🔄 GitHub and GitHub Actions**

GitHub provides a shared place for the source code, documentation, and change history. Team members can create a branch for a feature or fix, commit related changes with clear messages, and open a pull request so the group can review the changes before merging them into the main branch.

GitHub Actions can automatically run the Android Gradle build and available tests when code is pushed or a pull request is opened. The Actions tab shows each run and whether it passed. A successful build confirms that the project compiles in the runner; it does not replace checking the app on an emulator or Android device.

---

**▶️ How to Use**

1. Open the application after installing it from the provided APK in the GitHub repository.
2. Browse products from Home or Collection and select a category to filter the list.
3. Tap a product to view its details, then add it to the shopping bag.
4. Change quantities or remove products in the bag, then continue to the demonstration checkout.
5. Use the side menu to access your account, settings, and Help & User Guide.

---

**📝 Release Notes**

## Updated Application — Shopping, Account, and Navigation Improvements

Since the initial prototype, the app has been updated with:

- **Working category selection** so users can filter the collection by the selected category.
- **Product detail pages** showing the selected product's title, description, price, add-to-bag button, and suggested items.
- **Branded add-to-bag confirmation** using the website's orange, mint, and dark colours.
- **Improved shopping bag** with clearer product rows, quantity controls, removal actions, and an order summary.
- **Checkout demonstration page** for delivery address details with input validation. It does not process card payments.
- **Firebase account access** for email/password registration and login, plus a separate Google sign-in option.
- **Expanded settings** for account and shopping preferences, including password actions and product display options.
- **Slide-out navigation menu** linking the main app screens.
- **Expanded app-focused help** explaining how users navigate and shop in the app.
- **Interface refinements** to align screens with the supplied design, improve field colours, and prevent content from sitting under Android status-bar icons.
