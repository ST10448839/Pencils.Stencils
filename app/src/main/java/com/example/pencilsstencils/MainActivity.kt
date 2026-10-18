package com.example.pencilsstencils

import android.content.ContentValues
import android.content.Context
import android.app.Dialog
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import android.widget.*
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private data class Product(val id: Int, val name: String, val description: String, val price: Int, val category: String)

private fun productImageResource(productId: Int): Int = when (productId) {
    1 -> R.drawable.product_royal_ink_technical_pen
    2 -> R.drawable.product_studio_graphite_set
    3 -> R.drawable.product_watercolour_pocket_palette
    4 -> R.drawable.product_canvas_sketchbook
    5 -> R.drawable.product_precision_ruler_set
    6 -> R.drawable.product_charcoal_artist_sticks
    7 -> R.drawable.product_fine_liner_trio
    8 -> R.drawable.product_acrylic_starter_set
    9 -> R.drawable.product_creative_stencil_pack
    10 -> R.drawable.product_mixed_media_pad
    else -> R.drawable.product_pen
}

private class ShopDb(context: Context) : SQLiteOpenHelper(context, "pencils_stencils.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, price INTEGER NOT NULL, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL)")
        seedProducts(db)
        seedDemoUsers(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Keeps existing accounts and products while adding any new seed products.
        seedProducts(db)
    }
    private fun seedProducts(db: SQLiteDatabase) {
        val products = listOf(
            Product(1,"Royal Ink Technical Pen","Precision 0.5 mm nib for fine line work.",45000,"Pens"),
            Product(2,"Studio Graphite Set","Six soft graphite grades for sketching.",18900,"Pencils"),
            Product(3,"Watercolour Pocket Palette","Twelve vivid colours in a travel case.",32500,"Paint"),
            Product(4,"Canvas Sketchbook","A sturdy 120-page book for daily ideas.",24900,"Paper"),
            Product(5,"Precision Ruler Set","Three clear rulers for accurate layouts.",12900,"Tools"),
            Product(6,"Charcoal Artist Sticks","Rich charcoal for expressive shading.",9900,"Pencils"),
            Product(7,"Fine Liner Trio","Archival black liners in three tip sizes.",15900,"Pens"),
            Product(8,"Acrylic Starter Set","Six blendable acrylic colours.",29900,"Paint"),
            Product(9,"Creative Stencil Pack","Reusable geometric and botanical shapes.",13900,"Tools"),
            Product(10,"Mixed Media Pad","Heavyweight sheets for paint and ink.",21900,"Paper")
        )
        for (p in products) {
            val row = ContentValues().apply { put("id",p.id); put("name",p.name); put("description",p.description); put("price",p.price); put("category",p.category) }
            db.insertWithOnConflict("products", null, row, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
    private fun seedDemoUsers(db: SQLiteDatabase) {
        // Synthetic accounts satisfy the prototype's sample-data requirement; all use a salted hash.
        for (i in 1..10) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val row = ContentValues().apply {
                put("name", "Demo Customer %02d".format(i)); put("email", "demo%02d@example.test".format(i))
                val demoPassword = "DemoPass123!".toCharArray()
                put("salt", salt.toHex()); put("password_hash", demoPassword.hash(salt).toHex()); demoPassword.fill('\u0000'); put("created_at", System.currentTimeMillis())
            }
            db.insert("users", null, row)
        }
    }
    fun allProducts(): List<Product> {
        val result = mutableListOf<Product>()
        readableDatabase.rawQuery("SELECT id,name,description,price,category FROM products ORDER BY id", null).use { c ->
            while (c.moveToNext()) result.add(Product(c.getInt(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(4)))
        }
        return result
    }
    fun createUser(name: String, email: String, password: CharArray): Boolean {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("name",name.trim()); put("email",email.trim().lowercase()); put("salt",salt.toHex()); put("password_hash",password.hash(salt).toHex()); put("created_at",System.currentTimeMillis())
        }
        return try { writableDatabase.insertOrThrow("users",null,values); true } catch (_: android.database.sqlite.SQLiteConstraintException) { false } finally { password.fill('\u0000') }
    }
    fun authenticate(email: String, password: CharArray): String? {
        readableDatabase.rawQuery("SELECT name,salt,password_hash FROM users WHERE email=?", arrayOf(email.trim().lowercase())).use { c ->
            if (!c.moveToFirst()) { password.fill('\u0000'); return null }
            val salt = c.getString(1).hexToBytes()
            val candidate = password.hash(salt)
            password.fill('\u0000')
            return if (java.security.MessageDigest.isEqual(candidate, c.getString(2).hexToBytes())) c.getString(0) else null
        }
    }
    companion object {
        private fun CharArray.hash(salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(this, salt, 120_000, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
        }
        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
        private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var db: ShopDb
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val prefs by lazy { getSharedPreferences("shop", MODE_PRIVATE) }
    private val cart = linkedMapOf<Int, Int>()
    private var activeName: String? = null
    private var activeEmail: String? = null
    private var sessionToken: String? = null
    private var isLocalSession: Boolean = false
    private var apiProducts: List<Product> = emptyList()
    private val bg = 0xFF111113.toInt(); private val panel = 0xFF1B1A1D.toInt(); private val fieldFill = 0xFF242226.toInt(); private val orange = 0xFFFF673D.toInt(); private val mint = 0xFF62E8D0.toInt(); private val ink = 0xFFFFF0FC.toInt(); private val muted = 0xFFB9B3BE.toInt(); private val blue = 0xFF111A9B.toInt(); private val edge = 0xFF514D53.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        WindowInsetsControllerCompat(window,window.decorView).apply {
            isAppearanceLightStatusBars=false
            isAppearanceLightNavigationBars=false
        }
        db = ShopDb(this)
        val restoreSession=prefs.getBoolean("keep",false)
        if (!restoreSession && firebaseAuth.currentUser != null) firebaseAuth.signOut()
        val firebaseUser=if (restoreSession) firebaseAuth.currentUser else null
        sessionToken = if (restoreSession && firebaseUser == null) prefs.getString("token", null) else null
        isLocalSession=restoreSession && firebaseUser == null && prefs.getBoolean("local_session",false)
        activeName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
            ?: if (firebaseUser != null) prefs.getString("name", null) ?: firebaseUser.email?.substringBefore('@')
            else if (sessionToken != null || isLocalSession) prefs.getString("name", null) else null
        activeEmail = firebaseUser?.email
            ?: if (sessionToken != null || isLocalSession) prefs.getString("email", null) else null
        ApiClient.products { result -> result.value?.let { items -> apiProducts = items.map { Product(it.id, it.name, it.description, it.priceCents, it.category) } } }
        homeScreen()
    }
    private fun base(title: String, showHeader: Boolean=true): LinearLayout {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,12,22,24); background=texture() }
        val scroll = ScrollView(this).apply { isFillViewport=true; setBackgroundColor(bg); clipToPadding=true; addView(root) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0,bars.top,0,bars.bottom)
            insets
        }
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        if(showHeader) header(root)
        if(title.isNotBlank()) label(title, 30, ink, true, root)
        return root
    }
    private fun header(parent: LinearLayout) {
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,8,0,12) }
        val logo=ImageView(this).apply { setImageResource(R.drawable.brand_logo); contentDescription="Pencils and Stencils logo"; scaleType=ImageView.ScaleType.FIT_CENTER }
        bar.addView(logo,LinearLayout.LayoutParams(dp(52),dp(52)))
        val brand=TextView(this).apply { text="Pencils &\nStencils"; textSize=25f; setTextColor(ink); setTypeface(typeface,Typeface.BOLD_ITALIC); setPadding(dp(12),0,0,0) }
        bar.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        val menu=TextView(this).apply { text="☰"; textSize=30f; gravity=Gravity.CENTER; setTextColor(ink); background=shape(orange,blue,2); contentDescription="Open navigation menu"; isClickable=true; isFocusable=true; setOnClickListener { openDrawer() } }
        bar.addView(menu,LinearLayout.LayoutParams(dp(52),dp(52)))
        parent.addView(bar); val line=View(this).apply { setBackgroundColor(blue) }; parent.addView(line,LinearLayout.LayoutParams(-1,dp(4)))
    }
    private fun openDrawer() {
        val dialog=Dialog(this)
        val drawer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(44),dp(18),dp(20))
            background=GradientDrawable().apply { setColor(panel); cornerRadii=floatArrayOf(0f,0f,dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat(),0f,0f) }
        }
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val title=TextView(this).apply { text="STUDIO MENU"; textSize=13f; letterSpacing=.14f; setTextColor(mint); setTypeface(typeface,Typeface.BOLD) }
        top.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        TextView(this).apply { text="×"; textSize=30f; setTextColor(ink); gravity=Gravity.CENTER; contentDescription="Close menu"; setOnClickListener { dialog.dismiss() } }.also { top.addView(it,LinearLayout.LayoutParams(dp(40),dp(42))) }
        drawer.addView(top)
        label("Pencils & Stencils",24,ink,true,drawer).apply { setTypeface(typeface,Typeface.BOLD_ITALIC); setPadding(0,dp(18),0,dp(4)) }
        note(if(activeName==null) "Welcome, maker!" else "Signed in as $activeName",drawer).apply { setPadding(0,0,0,dp(18)) }
        val destinations=listOf(
            "⌂   Home" to { homeScreen() },
            "✎   Collection" to { collectionScreen() },
            "▣   Shopping bag  (${cart.values.sum()})" to { cartScreen() },
            (if(activeName==null) "↪   Login / create account" else "◎   My account") to { if(activeName==null) loginScreen() else settingsScreen() },
            "⚙   Settings" to { settingsScreen() },
            "?   Help & user guide" to { helpScreen() }
        )
        destinations.forEach { (text, destination) ->
            val item=TextView(this).apply {
                this.text=text; textSize=17f; setTextColor(ink); gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(12),0,dp(8),0); background=shape(0xFF242226.toInt(),edge,1)
                isClickable=true; isFocusable=true
                setOnClickListener { dialog.dismiss(); destination() }
            }
            drawer.addView(item,LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(8) })
        }
        if(activeName!=null) {
            val logout=TextView(this).apply { text="↩   Log out"; textSize=17f; setTextColor(orange); gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),0,0,0); setOnClickListener { dialog.dismiss(); logout() } }
            drawer.addView(logout,LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(8) })
        }
        dialog.setContentView(drawer)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(dp(300),-1)
            setGravity(Gravity.START or Gravity.TOP)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply { dimAmount=.68f }
        }
        dialog.show()
        dialog.window?.setLayout(dp(300),-1)
        dialog.window?.setGravity(Gravity.START or Gravity.TOP)
    }
    private fun dp(value:Int)= (value*resources.displayMetrics.density).toInt()
    private fun texture(): Drawable = object: Drawable() {
        private val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF343238.toInt() }
        override fun draw(canvas:Canvas) { canvas.drawColor(bg); val step=dp(22).toFloat(); var y=step; while(y<bounds.height()) { var x=step/2; while(x<bounds.width()) { canvas.drawCircle(x,y,dp(0.7f),dot); x+=step }; y+=step } }
        override fun setAlpha(alpha:Int) { dot.alpha=alpha }
        override fun setColorFilter(filter:android.graphics.ColorFilter?) { dot.colorFilter=filter }
        override fun getOpacity():Int = PixelFormat.OPAQUE
    }
    private fun dp(value:Float)= (value*resources.displayMetrics.density)
    private fun shape(color:Int,stroke:Int=0x00000000,width:Int=0): GradientDrawable = GradientDrawable().apply { setColor(color); setStroke(dp(width),stroke); cornerRadius=dp(8).toFloat() }
    private fun inputBox(): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), shape(fieldFill, mint, 2))
        addState(intArrayOf(), shape(fieldFill, edge, 2))
    }
    private fun card(parent:LinearLayout):LinearLayout {
        val frame=FrameLayout(this); val shadow=View(this).apply { setBackgroundColor(blue) }
        frame.addView(shadow,FrameLayout.LayoutParams(-1,-1).apply { leftMargin=dp(5); topMargin=dp(5) })
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14)); background=shape(panel,edge,2) }
        frame.addView(content,FrameLayout.LayoutParams(-1,-2).apply { rightMargin=dp(5); bottomMargin=dp(5) })
        parent.addView(frame,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(18) }); return content
    }
    private fun label(text: String, size: Int, color: Int=ink, bold: Boolean=false, parent: LinearLayout?=null): TextView = TextView(this).apply {
        this.text=text; textSize=size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0,8,0,10); parent?.addView(this)
    }
    private fun field(hintText: String, password: Boolean=false): EditText = EditText(this).apply {
        hint=hintText; setHintTextColor(muted); setTextColor(ink); textSize=16f; setSingleLine(true); inputType=if(password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setPadding(dp(14),dp(8),dp(14),dp(8)); background=inputBox(); backgroundTintList=null
    }
    private fun button(text: String, parent: LinearLayout, color: Int=orange, action: () -> Unit) { Button(this).apply { this.text=text; setTextColor(ink); backgroundTintList=android.content.res.ColorStateList.valueOf(color); setOnClickListener { action() }; parent.addView(this, LinearLayout.LayoutParams(if(parent.orientation==LinearLayout.HORIZONTAL) -2 else -1,-2).apply { topMargin=5; bottomMargin=5 }) } }
    private fun note(text: String, parent: LinearLayout): TextView = label(text,14,muted,false,parent)
    private fun googleSignInButton(parent:LinearLayout,remember:Boolean) {
        val button=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            setPadding(dp(16),0,dp(16),0)
            background=shape(0xFFFFFFFF.toInt(),0xFFDADCE0.toInt(),1)
            isClickable=true
            isFocusable=true
            contentDescription="Continue with Google"
            setOnClickListener { signInWithGoogle(remember) }
        }
        val icon=ImageView(this).apply { setImageResource(R.drawable.ic_google_g); contentDescription=null }
        button.addView(icon,LinearLayout.LayoutParams(dp(22),dp(22)).apply { rightMargin=dp(12) })
        val caption=TextView(this).apply { text="Continue with Google"; textSize=15f; setTextColor(0xFF202124.toInt()); typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
        button.addView(caption)
        parent.addView(button,LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(5); bottomMargin=dp(5) })
    }
    private fun signInWithGoogle(remember:Boolean) {
        val option=GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request=GetCredentialRequest.Builder().addCredentialOption(option).build()
        CredentialManager.create(this).getCredentialAsync(
            this,
            request,
            null,
            ContextCompat.getMainExecutor(this),
            object:CredentialManagerCallback<GetCredentialResponse,GetCredentialException> {
                override fun onResult(result:GetCredentialResponse) {
                    val credential=result.credential
                    if(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val googleCredential=GoogleIdTokenCredential.createFrom(credential.data)
                            val firebaseCredential=GoogleAuthProvider.getCredential(googleCredential.idToken,null)
                            firebaseAuth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                                val user=if(task.isSuccessful) task.result?.user else null
                                if(user!=null) {
                                    val email=user.email.orEmpty()
                                    val name=user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@').ifBlank { "Google user" }
                                    enter(name,email,remember=remember)
                                } else toast(firebaseAuthError(task.exception,"Google sign-in failed"))
                            }
                        } catch(_:GoogleIdTokenParsingException) {
                            toast("Google returned an unreadable sign-in response. Please try again.")
                        }
                    } else toast("Google did not return a supported sign-in credential. Try again.")
                }
                override fun onError(error:GetCredentialException) {
                    toast(if(error.type.contains("NoCredential")) "No Google account was available on this device." else error.localizedMessage ?: "Google sign-in was cancelled or failed.")
                }
            }
        )
    }
    private fun loginScreen() {
        val root=base("",false); val mark=ImageView(this).apply { setImageResource(R.drawable.brand_logo); contentDescription="Pencils and Stencils logo"; scaleType=ImageView.ScaleType.FIT_CENTER }; root.addView(mark,LinearLayout.LayoutParams(dp(108),dp(108)).apply { gravity=Gravity.CENTER; topMargin=dp(18) })
        label("Pencils &\nStencils",42,ink,true,root).apply { gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD_ITALIC) }
        label("Time to get messy!",22,muted,false,root).apply { gravity=Gravity.CENTER; setPadding(0,0,0,dp(26)) }
        val form=card(root); label("Login",30,ink,true,form); val underline=View(this).apply { setBackgroundColor(mint) }; form.addView(underline,LinearLayout.LayoutParams(dp(105),dp(4)))
        label("EMAIL ADDRESS",14,ink,true,form); val email=field("maker@messy-studio.com"); form.addView(email,LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin=dp(16) })
        label("PASSWORD",14,ink,true,form); val pass=field("••••••••",true); form.addView(pass,LinearLayout.LayoutParams(-1,dp(58)))
        val keep=CheckBox(this).apply { text="Keep me logged in"; setTextColor(muted); isChecked=prefs.getBoolean("keep",false); buttonTintList=android.content.res.ColorStateList.valueOf(mint) }; form.addView(keep)
        button("Login   →",form) {
            val e=email.text.toString().trim(); val p=pass.text.toString()
            if(!validEmail(e)||p.isEmpty()) toast("Enter a valid email and password")
            else firebaseAuth.signInWithEmailAndPassword(e,p).addOnCompleteListener(this) { task ->
                val user=if(task.isSuccessful) task.result?.user else null
                if(user!=null) enter(user.displayName?.takeIf { it.isNotBlank() } ?: e.substringBefore('@'),user.email ?: e,remember=keep.isChecked)
                else toast(firebaseAuthError(task.exception,"Sign in failed"))
            }
        }
        googleSignInButton(form,keep.isChecked)
        note("New to the studio?",form); button("Create account",form,blue) { registerScreen() }
        button("Back to home",form,edge) { homeScreen() }
        note("Sign in with your Google account or use your email and password.",form)
    }
    private fun registerScreen() {
        val root=base("",false); val mark=ImageView(this).apply { setImageResource(R.drawable.brand_logo); contentDescription="Pencils and Stencils logo"; scaleType=ImageView.ScaleType.FIT_CENTER }; root.addView(mark,LinearLayout.LayoutParams(dp(108),dp(108)).apply { gravity=Gravity.CENTER }); label("Pencils &\nStencils",38,ink,true,root).apply { gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD_ITALIC) }; label("Join our messy creative collective",19,muted,false,root).apply { gravity=Gravity.CENTER }
        val form=card(root); val name=field("Alex Maker"); val email=field("hello@creator.com"); val p=field("Password (8+ characters)",true); val confirm=field("Confirm password",true)
        listOf("FULL NAME" to name,"EMAIL ADDRESS" to email,"PASSWORD" to p,"CONFIRM PASSWORD" to confirm).forEach { (caption,input)-> label(caption,14,ink,true,form); form.addView(input,LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin=dp(14) }) }
        button("Create account   →",form) {
            val n=name.text.toString().trim(); val e=email.text.toString().trim(); val pw=p.text.toString(); val cp=confirm.text.toString()
            when {
                n.length<2 -> toast("Enter your name")
                !validEmail(e) -> toast("Enter a valid email address")
                pw.length<8 -> toast("Password must be at least 8 characters")
                pw!=cp -> toast("Passwords do not match")
                else -> firebaseAuth.createUserWithEmailAndPassword(e,pw).addOnCompleteListener(this) { task ->
                    val user=if(task.isSuccessful) task.result?.user else null
                    if(user==null) toast(firebaseAuthError(task.exception,"Account creation failed"))
                    else {
                        val profile=UserProfileChangeRequest.Builder().setDisplayName(n).build()
                        user.updateProfile(profile).addOnCompleteListener(this) {
                            enter(n,user.email ?: e,remember=false)
                            toast("Your Firebase account was created")
                        }
                    }
                }
            }
        }
        button("Back to login",form,blue) { loginScreen() }
        button("Back to home",form,edge) { homeScreen() }
    }
    private fun homeScreen() {
        val root=base("")
        val hero=card(root)
        label("EST. 2024",14,mint,true,hero).apply { gravity=Gravity.CENTER; letterSpacing=.16f }
        label("Tools for the\nCreative Mind",38,ink,true,hero).apply { gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD) }
        note("We curate the world’s most tactile, inspiring, and slightly messy art supplies. Your workspace is a playground—let’s keep it that way.",hero).apply { textSize=18f; gravity=Gravity.CENTER }
        button("Shop Best Sellers   →",hero) { collectionScreen() }
        button("Explore the Collection",hero,mint) { collectionScreen() }

        val account=card(root)
        if(activeName==null) {
            label("Welcome, maker!",25,ink,true,account)
            note("Sign in to pick up where you left off, or create an account to join the creative collective.",account).textSize=16f
            button("Login   →",account) { loginScreen() }
            button("Create an account",account,blue) { registerScreen() }
        } else {
            label("Welcome back, $activeName!",24,ink,true,account)
            note("Your creative workspace is ready.",account)
            button("Settings",account,mint) { settingsScreen() }
            button("Log out",account,edge) { logout() }
        }

        label("PICK YOUR NEXT PROJECT",13,mint,true,root).apply { letterSpacing=.13f }
        label("Shop by category",25,ink,true,root)
        val categories=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        listOf("Pens","Pencils","Paint","Paper","Tools").forEach { category ->
            button(category,categories,blue) { collectionScreen(category) }
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(categories) })

        label("Studio favourites",25,ink,true,root)
        val favourite=products().firstOrNull()
        if(favourite!=null) {
            val feature=card(root)
            feature.setOnClickListener { productDetailsScreen(favourite.id) }
            val photo=ImageView(this).apply { setImageResource(productImageResource(favourite.id)); contentDescription="View ${favourite.name} details"; scaleType=ImageView.ScaleType.CENTER_CROP; setOnClickListener { productDetailsScreen(favourite.id) } }
            feature.addView(photo,LinearLayout.LayoutParams(-1,dp(190)).apply { bottomMargin=dp(10) })
            label("BESTSELLER",12,mint,true,feature)
            label(favourite.name,22,ink,true,feature).setOnClickListener { productDetailsScreen(favourite.id) }
            note(favourite.description,feature)
            label(money(favourite.price),20,orange,true,feature)
            button("Add to bag",feature) { cart[favourite.id]=(cart[favourite.id]?:0)+1; showAddedToBag(favourite) }
        }
    }
    private fun collectionScreen(category:String?=null) {
        val allItems=products()
        val items=if(category.isNullOrBlank()) allItems else allItems.filter { it.category.equals(category,true) }
        val root=base(if(category.isNullOrBlank()) "Our Collection" else category)
        val filterRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val filters=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(filterRow) }
        listOf("All","Pens","Pencils","Paint","Paper","Tools").forEach { filter ->
            val active=(category==null && filter=="All") || category.equals(filter,true)
            button(filter,filterRow,if(active) orange else blue) { collectionScreen(if(filter=="All") null else filter) }
        }
        root.addView(filters,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        note("SHOWING ${items.size} ${if(category.isNullOrBlank()) "PRODUCTS" else category.uppercase()+" PRODUCTS"}",root).apply { letterSpacing=.18f }
        if(items.isEmpty()) {
            val empty=card(root); label("Nothing in this category yet",21,ink,true,empty); note("Choose another category to keep exploring.",empty)
        }
        for(p in items) {
            val productCard=card(root)
            productCard.setOnClickListener { productDetailsScreen(p.id) }
            if(prefs.getBoolean("show_images",true)) {
                val image=ImageView(this).apply { setImageResource(productImageResource(p.id)); contentDescription="View ${p.name} details"; scaleType=ImageView.ScaleType.CENTER_CROP; background=shape(panel,edge,1); setOnClickListener { productDetailsScreen(p.id) } }
                productCard.addView(image,LinearLayout.LayoutParams(-1,dp(215)).apply { bottomMargin=dp(15) })
            }
            label(p.category.uppercase(),12,mint,true,productCard)
            label(p.name,23,ink,true,productCard).setOnClickListener { productDetailsScreen(p.id) }
            if(prefs.getBoolean("show_descriptions",true)) note(p.description,productCard).apply { textSize=17f; setOnClickListener { productDetailsScreen(p.id) } }
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            label(money(p.price),22,ink,true).also { row.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }
            button("ADD TO BAG",row) { cart[p.id]=(cart[p.id]?:0)+1; showAddedToBag(p) }; productCard.addView(row)
        }
        navigation(root)
    }
    private fun productDetailsScreen(productId:Int) {
        val product=products().firstOrNull { it.id==productId }
        if(product==null) {
            toast("That product is no longer available")
            collectionScreen()
            return
        }

        val root=base("Product details")
        val details=card(root)
        val image=ImageView(this).apply {
            setImageResource(productImageResource(product.id))
            contentDescription=product.name
            scaleType=ImageView.ScaleType.CENTER_CROP
            background=shape(fieldFill,edge,1)
        }
        details.addView(image,LinearLayout.LayoutParams(-1,dp(245)).apply { bottomMargin=dp(12) })
        label(product.category.uppercase(),13,mint,true,details).apply { letterSpacing=.12f }
        label(product.name,27,ink,true,details)
        note(product.description,details).apply { textSize=17f; setPadding(0,dp(6),0,dp(10)) }
        label(money(product.price),26,orange,true,details)
        button("ADD TO BAG",details) {
            cart[product.id]=(cart[product.id]?:0)+1
            showAddedToBag(product)
        }
        button("Back to ${product.category}",details,blue) { collectionScreen(product.category) }

        label("Suggested items",25,ink,true,root).apply { setPadding(0,dp(8),0,dp(2)) }
        note("More tools from the collection",root)
        val suggestions=products().filter { it.id!=product.id }
        for(suggested in suggestions) {
            val suggestionCard=card(root)
            suggestionCard.setOnClickListener { productDetailsScreen(suggested.id) }
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            val thumbnail=ImageView(this).apply {
                setImageResource(productImageResource(suggested.id))
                contentDescription="View ${suggested.name} details"
                scaleType=ImageView.ScaleType.CENTER_CROP
                background=shape(fieldFill,edge,1)
                setOnClickListener { productDetailsScreen(suggested.id) }
            }
            row.addView(thumbnail,LinearLayout.LayoutParams(dp(92),dp(92)).apply { rightMargin=dp(14) })
            val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            label(suggested.category.uppercase(),11,mint,true,info)
            label(suggested.name,17,ink,true,info).setOnClickListener { productDetailsScreen(suggested.id) }
            label(money(suggested.price),16,orange,true,info)
            row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            suggestionCard.addView(row)
            button("View item  →",suggestionCard,blue) { productDetailsScreen(suggested.id) }
        }
        navigation(root)
    }
    private fun showAddedToBag(product:Product) {
        val dialog=Dialog(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(22),dp(22),dp(22),dp(18)); background=shape(panel,mint,2) }
        label("✓",38,mint,true,content).apply { gravity=Gravity.CENTER }
        label("Added to your bag",24,ink,true,content).apply { gravity=Gravity.CENTER }
        label(product.name,17,muted,false,content).apply { gravity=Gravity.CENTER }
        label(money(product.price),22,orange,true,content).apply { gravity=Gravity.CENTER }
        button("Continue shopping",content,blue) { dialog.dismiss() }
        button("View shopping bag",content) { dialog.dismiss(); cartScreen() }
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.88f).toInt(),-2)
    }
    private fun cartScreen() {
        val root=base("Your Bag")
        label("${cart.values.sum()} item${if(cart.values.sum()==1) "" else "s"} in your bag",15,mint,true,root)
        if(cart.isEmpty()) {
            val empty=card(root); label("Your bag is waiting for inspiration",22,ink,true,empty); note("Browse the collection and add your favourite tools.",empty)
            button("Explore the collection",empty) { collectionScreen() }
            return
        }
        var subtotal=0
        for((id,qty) in cart.toMap()) {
            val p=products().firstOrNull { it.id==id } ?: continue; subtotal+=p.price*qty
            val itemCard=card(root)
            val productRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            val image=ImageView(this).apply { setImageResource(productImageResource(p.id)); contentDescription=p.name; scaleType=ImageView.ScaleType.CENTER_CROP; background=shape(fieldFill,edge,1) }
            productRow.addView(image,LinearLayout.LayoutParams(dp(88),dp(88)).apply { rightMargin=dp(14) })
            val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            label(p.category.uppercase(),11,mint,true,info); label(p.name,18,ink,true,info); label("${money(p.price)} each",15,muted,false,info)
            productRow.addView(info,LinearLayout.LayoutParams(0,-2,1f)); itemCard.addView(productRow)
            val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(10),0,0) }
            val minus=TextView(this).apply { text="−"; textSize=22f; gravity=Gravity.CENTER; setTextColor(ink); background=shape(0xFF2A292D.toInt(),edge,1); setOnClickListener { if(qty<=1) cart.remove(id) else cart[id]=qty-1; cartScreen() } }
            val quantity=TextView(this).apply { text=qty.toString(); textSize=17f; gravity=Gravity.CENTER; setTextColor(ink) }
            val plus=TextView(this).apply { text="+"; textSize=22f; gravity=Gravity.CENTER; setTextColor(ink); background=shape(0xFF2A292D.toInt(),edge,1); setOnClickListener { cart[id]=qty+1; cartScreen() } }
            controls.addView(minus,LinearLayout.LayoutParams(dp(42),dp(40))); controls.addView(quantity,LinearLayout.LayoutParams(dp(46),dp(40))); controls.addView(plus,LinearLayout.LayoutParams(dp(42),dp(40)))
            val lineTotal=TextView(this).apply { text=money(p.price*qty); textSize=18f; setTextColor(orange); setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER_VERTICAL }
            controls.addView(lineTotal,LinearLayout.LayoutParams(0,dp(40),1f).apply { leftMargin=dp(8) })
            val remove=TextView(this).apply { text="REMOVE"; textSize=12f; letterSpacing=.08f; setTextColor(orange); gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),0,dp(4),0); setOnClickListener { cart.remove(id); cartScreen() } }
            controls.addView(remove); itemCard.addView(controls)
        }
        val shipping=if(subtotal==0) 0 else 6500
        val summary=card(root); label("Order Summary",25,ink,true,summary)
        summaryRow("Subtotal",money(subtotal),summary); summaryRow("Delivery",money(shipping),summary); summaryRow("VAT","Included",summary)
        val divider=View(this).apply { setBackgroundColor(edge) }; summary.addView(divider,LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(8); bottomMargin=dp(8) })
        summaryRow("Total",money(subtotal+shipping),summary,true)
        note("Delivery is a prototype flat rate of R65.00.",summary)
        button("Continue to checkout   →",summary) { checkoutScreen() }
        button("Continue shopping",root,blue) { collectionScreen() }
    }
    private fun summaryRow(name:String,value:String,parent:LinearLayout,total:Boolean=false) {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        label(name,if(total) 21 else 16,if(total) ink else muted,total).also { row.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }
        label(value,if(total) 24 else 16,if(total) orange else ink,true).also { row.addView(it) }
        parent.addView(row)
    }
    private fun checkoutScreen() {
        val root=base("Checkout")
        note("Delivery details",root).apply { textSize=19f; setTextColor(mint) }
        val address=card(root)
        val fullName=field("Full name"); val email=field("Email address"); val phone=field("Phone number"); val street=field("Street address and unit"); val city=field("Suburb / city"); val postal=field("Postal code")
        email.setText(activeEmail ?: "")
        listOf("FULL NAME" to fullName,"EMAIL" to email,"PHONE" to phone,"DELIVERY ADDRESS" to street,"SUBURB / CITY" to city,"POSTAL CODE" to postal).forEach { (caption,input) ->
            label(caption,12,ink,true,address); address.addView(input,LinearLayout.LayoutParams(-1,dp(54)).apply { bottomMargin=dp(10) })
        }
        note("Payment details · prototype only",root).apply { textSize=19f; setTextColor(mint) }
        val payment=card(root)
        val warning=TextView(this).apply { text="Demo checkout only. Do not enter real bank card details. Nothing entered here is saved or sent, and no payment will be taken."; textSize=14f; setTextColor(ink); setPadding(dp(12),dp(10),dp(12),dp(10)); background=shape(0xFF3A2925.toInt(),orange,1) }
        payment.addView(warning,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        val cardName=field("Name on card"); val cardNumber=field("Card number (demo only)"); val expiry=field("MM/YY"); val cvv=field("CVV")
        cardNumber.inputType=InputType.TYPE_CLASS_NUMBER; cardNumber.filters=arrayOf(android.text.InputFilter.LengthFilter(19))
        expiry.inputType=InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
        cvv.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; cvv.filters=arrayOf(android.text.InputFilter.LengthFilter(4))
        listOf("NAME ON CARD" to cardName,"CARD NUMBER" to cardNumber,"EXPIRY DATE" to expiry,"SECURITY CODE" to cvv).forEach { (caption,input) ->
            label(caption,12,ink,true,payment); payment.addView(input,LinearLayout.LayoutParams(-1,dp(54)).apply { bottomMargin=dp(10) })
        }
        val total=cart.entries.sumOf { (id,qty) -> (products().firstOrNull { it.id==id }?.price ?: 0)*qty } + 6500
        val totalCard=card(root); label("Amount due",16,muted,false,totalCard); label(money(total),26,orange,true,totalCard)
        button("Place demo order",totalCard) {
            val digits=cardNumber.text.toString().filter { it.isDigit() }
            val addressOk=fullName.text.isNotBlank() && validEmail(email.text.toString()) && phone.text.toString().filter { it.isDigit() }.length>=7 && street.text.isNotBlank() && city.text.isNotBlank() && postal.text.isNotBlank()
            val cardOk=cardName.text.isNotBlank() && digits.length in 12..19 && expiry.text.toString().matches(Regex("(0[1-9]|1[0-2])/[0-9]{2}")) && cvv.text.toString().matches(Regex("[0-9]{3,4}"))
            when {
                !addressOk -> toast("Complete your name, contact and delivery address")
                !cardOk -> toast("Enter the demo card details in the requested format")
                else -> {
                    cart.clear()
                    val confirmation=Dialog(this)
                    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(22),dp(22),dp(22),dp(18)); background=shape(panel,mint,2) }
                    label("Order received!",25,ink,true,body).apply { gravity=Gravity.CENTER }
                    note("This was a prototype checkout. No payment was processed and card details were not saved.",body).apply { gravity=Gravity.CENTER }
                    button("Back to the studio",body) { confirmation.dismiss(); homeScreen() }
                    confirmation.setContentView(body); confirmation.window?.setBackgroundDrawableResource(android.R.color.transparent); confirmation.show(); confirmation.window?.setLayout((resources.displayMetrics.widthPixels*.88f).toInt(),-2)
                }
            }
        }
        button("Back to bag",root,blue) { cartScreen() }
    }
    private fun settingsScreen() {
        val root=base("Settings")
        val account=card(root); label("YOUR ACCOUNT",13,mint,true,account)
        label(activeName ?: "Guest maker",22,ink,true,account)
        note(activeEmail?.takeIf { it.isNotBlank() } ?: "Sign in to use your account across sessions.",account)
        val signedInUser=firebaseAuth.currentUser
        if(signedInUser!=null) {
            button("Change display name",account,blue) { showChangeDisplayNameDialog() }
            val hasPasswordLogin=signedInUser.providerData.any { it.providerId==EmailAuthProvider.PROVIDER_ID }
            if(hasPasswordLogin) {
                button("Change password",account) { showChangePasswordDialog() }
                button("Send password reset email",account,blue) { sendPasswordResetEmail() }
                note("Password changes require your current password. Reset instructions are sent to your account email.",account)
            } else {
                note("This account uses Google sign-in. Manage its password through your Google account.",account)
            }
        } else {
            note("Log in to manage your account details and password.",account)
            button("Log in",account) { loginScreen() }
        }
        val remember=Switch(this).apply { text="Keep me logged in on this device"; setTextColor(ink); isChecked=prefs.getBoolean("keep",false); buttonTintList=android.content.res.ColorStateList.valueOf(mint); setOnCheckedChangeListener { _,value->
            val editor=prefs.edit().putBoolean("keep",value)
            if(value && activeName!=null && sessionToken!=null) editor.putString("token",sessionToken).putString("name",activeName).putString("email",activeEmail ?: "").putBoolean("local_session",false)
            else if(value && activeName!=null && isLocalSession) editor.putString("name",activeName).putString("email",activeEmail ?: "").putBoolean("local_session",true).remove("token")
            else if(!value) editor.remove("token").remove("name").remove("email").remove("local_session")
            editor.apply()
        } }
        account.addView(remember)
        val shopping=card(root); label("SHOPPING PREFERENCES",13,mint,true,shopping)
        val sortLabel=label("Sort products by",16,ink,true,shopping)
        val sortOptions=listOf("Featured", "Price: low to high", "Price: high to low", "Name: A to Z")
        val sort=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,sortOptions)
            setSelection(sortOptions.indexOf(prefs.getString("sort_order","Featured")).coerceAtLeast(0))
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) { prefs.edit().putString("sort_order",sortOptions[position]).apply() }
            }
        }
        shopping.addView(sort,LinearLayout.LayoutParams(-1,dp(52)))
        val images=Switch(this).apply { text="Show product photos"; setTextColor(ink); isChecked=prefs.getBoolean("show_images",true); buttonTintList=android.content.res.ColorStateList.valueOf(mint); setOnCheckedChangeListener { _,value->prefs.edit().putBoolean("show_images",value).apply() } }
        val descriptions=Switch(this).apply { text="Show product descriptions"; setTextColor(ink); isChecked=prefs.getBoolean("show_descriptions",true); buttonTintList=android.content.res.ColorStateList.valueOf(mint); setOnCheckedChangeListener { _,value->prefs.edit().putBoolean("show_descriptions",value).apply() } }
        val confirmBagClear=Switch(this).apply { text="Confirm before clearing the bag"; setTextColor(ink); isChecked=prefs.getBoolean("confirm_bag_clear",true); buttonTintList=android.content.res.ColorStateList.valueOf(mint); setOnCheckedChangeListener { _,value->prefs.edit().putBoolean("confirm_bag_clear",value).apply() } }
        shopping.addView(images); shopping.addView(descriptions); shopping.addView(confirmBagClear)
        note("Prices are shown in South African rand (R). Product display preferences are saved on this device.",shopping)
        button("Reset shopping preferences",shopping,blue) {
            prefs.edit().putString("sort_order","Featured").putBoolean("show_images",true).putBoolean("show_descriptions",true).putBoolean("confirm_bag_clear",true).apply()
            toast("Shopping preferences reset")
            settingsScreen()
        }
        val app=card(root); label("APP AND SUPPORT",13,mint,true,app)
        button("Browse the collection",app) { collectionScreen() }
        button("Help & user guide",app,blue) { helpScreen() }
        button("Clear shopping bag",app,edge) {
            if(cart.isEmpty()) toast("Your shopping bag is already empty")
            else if(prefs.getBoolean("confirm_bag_clear",true)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Clear shopping bag?")
                    .setMessage("This removes all items from your bag.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Clear bag") { _,_ -> cart.clear(); toast("Shopping bag cleared"); settingsScreen() }
                    .show()
            } else { cart.clear(); toast("Shopping bag cleared"); settingsScreen() }
        }
        button("Back to Home",root,blue) { homeScreen() }
    }
    private fun showChangeDisplayNameDialog() {
        val user=firebaseAuth.currentUser ?: run { toast("Log in to change your display name"); return }
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0) }
        val nameField=field("Your display name").apply { setText(activeName ?: user.displayName.orEmpty()) }
        form.addView(nameField,LinearLayout.LayoutParams(-1,dp(54)))
        val dialog=android.app.AlertDialog.Builder(this)
            .setTitle("Change display name")
            .setView(form)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName=nameField.text.toString().trim()
                if(newName.length<2) { nameField.error="Enter at least 2 characters"; return@setOnClickListener }
                val update=UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                user.updateProfile(update).addOnCompleteListener(this) { task ->
                    if(task.isSuccessful) {
                        activeName=newName
                        if(prefs.getBoolean("keep",false)) prefs.edit().putString("name",newName).apply()
                        dialog.dismiss()
                        toast("Display name updated")
                        settingsScreen()
                    } else toast(firebaseAuthError(task.exception,"Could not update display name"))
                }
            }
        }
        dialog.show()
    }
    private fun showChangePasswordDialog() {
        val user=firebaseAuth.currentUser ?: run { toast("Log in to change your password"); return }
        val email=user.email ?: run { toast("This account does not have an email address"); return }
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0) }
        val current=field("Current password",true)
        val newPassword=field("New password (8+ characters)",true)
        val confirm=field("Confirm new password",true)
        listOf(current,newPassword,confirm).forEach { form.addView(it,LinearLayout.LayoutParams(-1,dp(54)).apply { bottomMargin=dp(8) }) }
        val dialog=android.app.AlertDialog.Builder(this)
            .setTitle("Change password")
            .setView(form)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Update",null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val old=current.text.toString()
                val replacement=newPassword.text.toString()
                val repeated=confirm.text.toString()
                when {
                    old.isBlank() -> current.error="Enter your current password"
                    replacement.length<8 -> newPassword.error="Use at least 8 characters"
                    replacement==old -> newPassword.error="Choose a password different from your current one"
                    replacement!=repeated -> confirm.error="The passwords do not match"
                    else -> {
                        val credential=EmailAuthProvider.getCredential(email,old)
                        user.reauthenticate(credential).addOnCompleteListener(this) { authTask ->
                            if(!authTask.isSuccessful) {
                                toast(firebaseAuthError(authTask.exception,"Current password is incorrect"))
                            } else {
                                user.updatePassword(replacement).addOnCompleteListener(this) { updateTask ->
                                    if(updateTask.isSuccessful) {
                                        dialog.dismiss()
                                        toast("Password updated successfully")
                                    } else toast(firebaseAuthError(updateTask.exception,"Could not update password. Try signing in again."))
                                }
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }
    private fun sendPasswordResetEmail() {
        val email=firebaseAuth.currentUser?.email ?: activeEmail
        if(email.isNullOrBlank()) { toast("Your account does not have an email address"); return }
        firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener(this) { task ->
            if(task.isSuccessful) toast("Password reset instructions sent to $email")
            else toast(firebaseAuthError(task.exception,"Could not send the reset email"))
        }
    }
    private fun helpScreen() {
        val root=base("Help & User Guide")
        note("How to browse, choose products, manage your bag, and personalise the app.",root).textSize=16f
        helpSection(root,"GETTING AROUND",listOf(
            "Tap the orange ☰ button in the top bar to open navigation from the side.",
            "Home shows the welcome screen, popular product, and category shortcuts. Collection shows the full product list.",
            "Use the category buttons at the top of Collection to filter products. Choose All to see the full list again."
        ))
        helpSection(root,"BROWSE PRODUCT DETAILS",listOf(
            "Tap a product photo, name, or card in Collection to open its details page.",
            "The details page shows the product photo, category, name, description, and price.",
            "Tap a suggested item or View item to open that product's details. Use Back to return to that product's category."
        ))
        helpSection(root,"SHOPPING BAG AND CHECKOUT",listOf(
            "Tap Add to bag on a product. A confirmation appears, and the shopping bag count updates in the menu.",
            "In the bag, use + and − to change quantities, or Remove to delete a product. The order summary updates automatically.",
            "Checkout asks for delivery details and demo card fields. This is a prototype: it does not charge a card or save or send card details."
        ))
        helpSection(root,"ACCOUNT ACCESS",listOf(
            "Choose Login / create account from the menu or Home. To register, enter your name, email, and a password of at least 8 characters, then confirm the password.",
            "Sign in with your email and password, or tap Continue with Google if you use Google sign-in.",
            "Keep me logged in controls whether your account stays signed in when you reopen the app. Choose Log out from the menu to sign out."
        ))
        helpSection(root,"SETTINGS",listOf(
            "Change display name updates the name shown for your account.",
            "For an email-and-password account, Change password asks for your current password before saving a new one. Send password reset email sends instructions to your account email.",
            "Google accounts use Google to manage their password. Change it in your Google account instead of in this app.",
            "Choose a product sort order, show or hide product photos and descriptions, and decide whether the app asks before clearing the bag.",
            "Reset shopping preferences restores the default sort order and turns photos, descriptions, and clear-bag confirmation back on."
        ))
        helpSection(root,"QUICK TIPS",listOf(
            "If sign-in does not work, check your email and password and make sure your device has internet access.",
            "If the product list does not load, check your internet connection and reopen Collection. The app may show its sample products while the full list is unavailable.",
            "The bag is kept while the app is open. It may be empty after you close and reopen the app."
        ))
        button("Back to Home",root) { homeScreen() }
    }
    private fun helpSection(parent: LinearLayout,title:String,items:List<String>) {
        val section=card(parent)
        label(title,16,mint,true,section).apply { letterSpacing=.08f }
        items.forEach { item ->
            val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.TOP }
            label("•",18,orange,true).also { line.addView(it,LinearLayout.LayoutParams(dp(20),-2)) }
            label(item,15,muted,false).also { line.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }
            section.addView(line)
        }
    }
    private fun navigation(root: LinearLayout) { button("Home",root) { homeScreen() }; button("Settings",root) { settingsScreen() } }
    private fun products(): List<Product> {
        val items=apiProducts.ifEmpty { db.allProducts() }
        return when(prefs.getString("sort_order","Featured")) {
            "Price: low to high" -> items.sortedBy { it.price }
            "Price: high to low" -> items.sortedByDescending { it.price }
            "Name: A to Z" -> items.sortedBy { it.name.lowercase() }
            else -> items.sortedBy { it.id }
        }
    }
    private fun logout() {
        sessionToken?.let { ApiClient.logout(it) }
        firebaseAuth.signOut()
        sessionToken=null
        isLocalSession=false
        activeName=null
        activeEmail=null
        prefs.edit().clear().apply()
        homeScreen()
    }
    private fun enter(name: String, email: String?=null, token: String?=null, remember: Boolean=false, localSession:Boolean=false) {
        activeName=name
        activeEmail=email
        sessionToken=token
        isLocalSession=localSession
        val editor=prefs.edit().putBoolean("keep",remember)
        if(remember && firebaseAuth.currentUser!=null) editor.putString("name",name).putString("email",email ?: "").remove("token").putBoolean("local_session",false)
        else if(remember && token!=null) editor.putString("name",name).putString("email",email ?: "").putString("token",token).putBoolean("local_session",false)
        else if(remember && localSession) editor.putString("name",name).putString("email",email ?: "").remove("token").putBoolean("local_session",true)
        else editor.remove("name").remove("email").remove("token").remove("local_session")
        editor.apply()
        homeScreen()
    }
    private fun isApiUnavailable(error:String?):Boolean = error?.startsWith("Can't reach") == true
    private fun firebaseAuthError(error:Exception?, fallback:String):String = when((error as? FirebaseAuthException)?.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has a Firebase account. Try logging in instead."
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password with at least 8 characters."
        "ERROR_USER_NOT_FOUND" -> "No Firebase account was found for that email. Register first."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "The email or password is incorrect."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Could not reach Firebase. Check your internet connection and try again."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a little, then try again."
        else -> error?.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
    private fun validEmail(s: String)=android.util.Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()
    private fun money(cents: Int)="R ${"%.2f".format(cents/100.0)}"
    private fun toast(s: String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
