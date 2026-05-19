package com.twoskoops707.sixdegrees.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.data.UserProfile
import com.twoskoops707.sixdegrees.data.UserProfileManager
import com.twoskoops707.sixdegrees.databinding.FragmentApiSignupWebBinding

data class ApiSignupEntry(val name: String, val url: String)

class ApiSignupWebFragment : Fragment() {

    private var _binding: FragmentApiSignupWebBinding? = null
    private val binding get() = _binding!!
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var profileManager: UserProfileManager

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private var captchaDetected = false
    private var formFilled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentApiSignupWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiKeyManager = ApiKeyManager(requireContext())
        profileManager = UserProfileManager(requireContext())

        val apiName = arguments?.getString("apiName") ?: ""
        val signupUrl = arguments?.getString("signupUrl") ?: ""
        val queueJson = arguments?.getString("queueJson") ?: "[]"
        val queuePosition = arguments?.getInt("queuePosition") ?: 0

        val queue = parseQueue(queueJson)
        val queueTotal = queue.size
        val isQueued = queueTotal > 1

        binding.webToolbar.title = if (isQueued)
            "${queuePosition + 1}/$queueTotal · $apiName"
        else
            apiName.ifBlank { "API Signup" }

        if (isQueued) {
            binding.tvQueueProgress.visibility = View.VISIBLE
            binding.tvQueueProgress.text = "Signing up for $apiName — auto-filling form…"
            binding.queueProgressBar.visibility = View.VISIBLE
            binding.queueProgressBar.max = queueTotal
            binding.queueProgressBar.progress = queuePosition + 1
        } else {
            binding.tvQueueProgress.visibility = View.GONE
            binding.queueProgressBar.visibility = View.GONE
        }

        binding.webToolbar.setNavigationOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
            else findNavController().navigateUp()
        }

        if (isQueued && queuePosition + 1 < queueTotal) {
            binding.btnSkip.visibility = View.VISIBLE
            binding.btnSkip.setOnClickListener { advanceQueue(queue, queuePosition, queueJson) }
        } else {
            binding.btnSkip.visibility = View.GONE
        }

        val profile = profileManager.load()

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-N975U1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onFillComplete(count: Int, fields: String, hasCaptcha: Boolean) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    formFilled = count > 0
                    captchaDetected = hasCaptcha
                    when {
                        hasCaptcha && count > 0 -> {
                            binding.tvFillStatus.text = "Auto-filled $count field(s). CAPTCHA detected — please solve it, then tap SUBMIT"
                            binding.tvFillStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
                            binding.tvFillStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning_dim))
                        }
                        hasCaptcha -> {
                            binding.tvFillStatus.text = "CAPTCHA detected — please fill the form and solve it, then tap SUBMIT"
                            binding.tvFillStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
                            binding.tvFillStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning_dim))
                        }
                        count > 0 -> {
                            binding.tvFillStatus.text = "Auto-filled $count field(s): $fields — submitting…"
                            binding.tvFillStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                            binding.tvFillStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_dim))
                            if (!hasCaptcha) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (_binding != null) autoSubmit()
                                }, 1000)
                            }
                        }
                        else -> {
                            binding.tvFillStatus.text = "Form not detected — fill manually, then paste your API key below"
                            binding.tvFillStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
                            binding.tvFillStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning_dim))
                        }
                    }
                    binding.tvFillStatus.visibility = View.VISIBLE
                }
            }

            @JavascriptInterface
            fun onSubmitDetected(success: Boolean) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    if (success) {
                        binding.tvFillStatus.text = "Submitted! Check your email for verification link."
                        binding.tvFillStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                        binding.tvFillStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_dim))
                        binding.tvFillStatus.visibility = View.VISIBLE
                    }
                }
            }
        }, "Android")

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (_binding == null) return
                binding.webProgress.visibility = View.VISIBLE
                binding.tvFillStatus.visibility = View.GONE
                formFilled = false
                captchaDetected = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (_binding == null) return
                binding.webProgress.visibility = View.GONE
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding != null) {
                        view.evaluateJavascript(buildFillScript(profile), null)
                    }
                }, 1500)
            }
        }

        if (signupUrl.isNotBlank()) binding.webView.loadUrl(signupUrl)

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (key.isBlank()) {
                Toast.makeText(requireContext(), "Paste your API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveKeyForApi(apiName, key)
            Toast.makeText(requireContext(), "Key saved for $apiName", Toast.LENGTH_SHORT).show()
            if (isQueued && queuePosition + 1 < queueTotal) {
                advanceQueue(queue, queuePosition, queueJson)
            } else {
                findNavController().navigateUp()
            }
        }
    }

    private fun autoSubmit() {
        binding.webView.evaluateJavascript("""
(function(){
  var btn = document.querySelector(
    'button[type="submit"], input[type="submit"], button.submit-btn, ' +
    'button#submit, button#register, button#sign-up, button#create-account, ' +
    'button.btn-primary[type="submit"], button.btn-submit, ' +
    '[data-testid="submit"], [data-testid="register"], [data-testid="sign-up"]'
  );
  if(btn && !btn.disabled){
    btn.click();
    Android.onSubmitDetected(true);
    return 'clicked';
  }
  return 'not_found';
})();
        """.trimIndent(), null)
    }

    private fun advanceQueue(queue: List<ApiSignupEntry>, currentPos: Int, queueJson: String) {
        val nextPos = currentPos + 1
        if (nextPos >= queue.size) {
            findNavController().navigateUp()
            return
        }
        val next = queue[nextPos]
        val bundle = bundleOf(
            "apiName" to next.name,
            "signupUrl" to next.url,
            "queueJson" to queueJson,
            "queuePosition" to nextPos
        )
        findNavController().navigate(R.id.action_signup_web_self, bundle)
    }

    private fun parseQueue(json: String): List<ApiSignupEntry> {
        return try {
            val type = Types.newParameterizedType(List::class.java, ApiSignupEntry::class.java)
            moshi.adapter<List<ApiSignupEntry>>(type).fromJson(json) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun saveKeyForApi(apiName: String, key: String) {
        val n = apiName.lowercase().replace(" ", "").replace(".", "").replace("-", "").replace("_", "")
        when {
            n.contains("haveibeenpwned") || n.contains("hibp") -> apiKeyManager.hibpKey = key
            n.contains("hunter") -> apiKeyManager.hunterKey = key
            n.contains("peopledatalabs") || n.contains("pdl") -> apiKeyManager.pdlKey = key
            n.contains("numverify") -> apiKeyManager.numverifyKey = key
            n.contains("shodan") -> apiKeyManager.shodanKey = key
            n.contains("pipl") -> apiKeyManager.piplKey = key
            n.contains("ipqualityscore") || n.contains("ipqs") -> apiKeyManager.ipqsKey = key
            n.contains("fullcontact") -> apiKeyManager.fullcontactKey = key
            n.contains("veriphone") -> apiKeyManager.veriphoneKey = key
            n.contains("googlecustom") || n.contains("googlecse") -> apiKeyManager.googleCseApiKey = key
            n.contains("bing") -> apiKeyManager.bingSearchKey = key
            n.contains("clearbit") -> apiKeyManager.clearbitKey = key
            n.contains("builtwith") -> apiKeyManager.builtWithKey = key
            n.contains("virustotal") -> apiKeyManager.virusTotalKey = key
            n.contains("abuseipdb") -> apiKeyManager.abuseIpDbKey = key
            n.contains("urlscan") -> apiKeyManager.urlScanKey = key
        }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "")

    private fun buildFillScript(p: UserProfile): String = """
(function(){
  var d={fn:'${esc(p.firstName)}',ln:'${esc(p.lastName)}',full:'${esc(p.fullName)}',
    email:'${esc(p.email)}',phone:'${esc(p.phone)}',co:'${esc(p.company)}',
    web:'${esc(p.website)}',city:'${esc(p.city)}',state:'${esc(p.state)}',
    zip:'${esc(p.zip)}',country:'${esc(p.country)}',addr:'${esc(p.address)}'};
  var inpD=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');
  var nsI=inpD?inpD.set:null;
  var taD=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value');
  var nsT=taD?taD.set:null;
  function sv(el,v){
    if(!v||!v.trim())return false;
    if(el.value&&el.value.trim())return false;
    try{
      if(el.tagName==='INPUT'&&nsI)nsI.call(el,v);
      else if(el.tagName==='TEXTAREA'&&nsT)nsT.call(el,v);
      else if(el.tagName==='SELECT'){
        for(var i=0;i<el.options.length;i++){
          var ot=el.options[i].text.toLowerCase(),ov=el.options[i].value.toLowerCase();
          if(ot.includes(v.toLowerCase())||ov===v.toLowerCase().substring(0,2)){el.selectedIndex=i;break;}
        }
      }else el.value=v;
      el.dispatchEvent(new Event('input',{bubbles:true}));
      el.dispatchEvent(new Event('change',{bubbles:true}));
      return true;
    }catch(e){return false;}
  }
  function gl(el){
    try{
      if(el.id){var l=document.querySelector('label[for="'+el.id+'"]');if(l)return(l.textContent||'').toLowerCase();}
      var p=el.closest('label');if(p)return(p.textContent||'').toLowerCase();
      var a=el.getAttribute('aria-labelledby');if(a){var ae=document.getElementById(a);if(ae)return(ae.textContent||'').toLowerCase();}
    }catch(e){}
    return '';
  }
  function sc(el,pts){
    var s=' '+(el.id||'').toLowerCase()+' '+(el.name||'').toLowerCase()+' '+
          (el.placeholder||'').toLowerCase()+' '+(el.getAttribute('aria-label')||'').toLowerCase()+
          ' '+(el.getAttribute('autocomplete')||'').toLowerCase()+' '+gl(el)+' ';
    for(var p of pts){if(s.includes(p))return true;}
    return false;
  }
  var inputs=Array.from(document.querySelectorAll(
    'input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]):not([type=file]),textarea,select'));
  var rules=[
    {v:d.fn,   pts:['first name','first-name','firstname','fname','given name','given-name']},
    {v:d.ln,   pts:['last name','last-name','lastname','lname','surname','family name']},
    {v:d.full, pts:[' name ','full name','full-name','fullname','your name','contact name']},
    {v:d.email,pts:['email','e-mail',' mail']},
    {v:d.phone,pts:['phone','telephone','mobile',' tel ','cell']},
    {v:d.co,   pts:['company','organization','organisation','employer','business','firm']},
    {v:d.web,  pts:['website','domain','homepage','web site','site url']},
    {v:d.city, pts:['city',' town ','locality']},
    {v:d.state,pts:['state','province','region']},
    {v:d.zip,  pts:['zip','postal','postcode','post code']},
    {v:d.country,pts:['country']},
    {v:d.addr, pts:['address','street']}
  ];
  var filled=[],n=0;
  for(var r of rules){
    for(var inp of inputs){
      if(sc(inp,r.pts)&&sv(inp,r.v)){filled.push(r.pts[0].trim());n++;break;}
    }
  }
  var hasCaptcha=!!(
    document.querySelector('iframe[src*="recaptcha"],iframe[src*="hcaptcha"],iframe[src*="turnstile"],'
    +'.g-recaptcha,.h-captcha,.cf-turnstile,[data-sitekey]')
  );
  Android.onFillComplete(n,filled.join(', '),hasCaptcha);
})();
""".trimIndent()

    override fun onDestroyView() {
        binding.webView.stopLoading()
        binding.webView.removeAllViews()
        super.onDestroyView()
        binding.webView.destroy()
        _binding = null
    }
}
