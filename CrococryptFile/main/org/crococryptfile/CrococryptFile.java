package org.crococryptfile;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import javax.crypto.Cipher;

import org.crococryptfile.ConsoleOptions.ConsoleOptions_Params;
import org.crococryptfile.datafile.CrocoFilereader;
import org.crococryptfile.datafile.CrocoFilewriter;
import org.crococryptfile.suites.BasicFileinfo;
import org.crococryptfile.suites.SUITES;
import org.crococryptfile.suites.Suite;
import org.crococryptfile.suites.SuiteMODE;
import org.crococryptfile.suites.SuitePARAM;
import org.crococryptfile.ui.CbIDecrypt;
import org.crococryptfile.ui.CbIEncrypt;
import org.crococryptfile.ui.UICenter;
import org.crococryptfile.ui.cui.CPrint;
import org.crococryptfile.ui.cui.PasswordCreateConsole;
import org.crococryptfile.ui.cui.PasswordInputConsole;
import org.crococryptfile.ui.cui.StatusConsole;
import org.crococryptfile.ui.gui.CrocoparamsDialog;
import org.crococryptfile.ui.gui.Page;
import org.crococryptfile.ui.gui.PageLauncher;
import org.crococryptfile.ui.gui.PageLauncher.Options;
import org.crococryptfile.ui.gui.PasswordCreatedialog;
import org.crococryptfile.ui.gui.PasswordInputdialog;
import org.crococryptfile.ui.gui.ProgressDoubleWindow;
import org.crococryptfile.ui.gui.ProgressWindow;
import org.crococryptfile.ui.gui.SimpleDialogs;
import org.crococryptfile.ui.gui.pages.CAPI_DNList;
import org.crococryptfile.ui.gui.pages.Decrypt;
import org.crococryptfile.ui.gui.pages.Encrypt;
import org.crococryptfile.ui.gui.pages.JCEPolicyError;
import org.crococryptfile.ui.resources._T;
import org.fhissen.callbacks.SUCCESS;
import org.fhissen.callbacks.SimpleCallback;
import org.fhissen.callbacks.SuccessCallback;
import org.fhissen.crypto.CryptoCodes;
import org.fhissen.crypto.InitCrypto;
import org.fhissen.utils.os.OSFolders;
import org.fhissen.utils.ui.StatusUpdate;


@SuppressWarnings("rawtypes")
public class CrococryptFile implements CbIEncrypt, CbIDecrypt, SimpleCallback{
	static{
		UICenter.INIT();
		InitCrypto.INIT();
	}
	
	public static void main(String[] args) throws Exception{
			if(args == null || args.length == 0){
				launch(null);
			}
			else{
				launch(ConsoleOptions.parse(args));
			}
	}
	
	public static final void launch(CrococryptParameters params){
		boolean jceok = true;
		try {
			if(Cipher.getMaxAllowedKeyLength("AES") < 256) jceok = false;
		} catch (Exception e) {
			jceok = false;
			e.printStackTrace();
		}
		if(!jceok){
			CPrint.error(_T.Launcher_jcepolicyerr);
			if(UICenter.isGUI()){
				PageLauncher.launch(new JCEPolicyError());
			}
			return;
		}
		
		if(System.console() != null && params == null)
			System.out.println("You can use the commandline interface by starting with -c");
		
		try{
			new CrococryptFile(params).main();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	public static String LASTERROR = null;
	
	private boolean check_forcedec = true;
	private boolean check_forceparams = true;
	private CrococryptParameters inputparams = null;
	private HashMap<SuitePARAM, Object> operationParams = new HashMap<>();
	private SuitePARAM[] reqparams = null;
	private int reqparams_index = 0;
	private Object currentSource;
	
	public CrococryptFile(CrococryptParameters params){
		this.inputparams = params;
		
		if(params != null && params.forceconsole)
			UICenter.setCUI();
	}
	
	private enum STATE{
		none,

		loadparams,
		dec4enc,
		operationparam,
		decrypt,
		decryptfinishoperation,
		encrypt,
		encryptfinishoperation,
	}
	
	private STATE cur = STATE.none;
	private SuccessCallback successcb = new SuccessCallback() {
		@Override public void callbackValue(Object source, SUCCESS ret) {
			CrococryptFile.this.callbackValue(source, ret);
		}
	};

	@SuppressWarnings("unchecked")
	public void main(){
		if(UICenter.isCUI() && (inputparams.destination == null || inputparams.filesanddirs == null || inputparams.filesanddirs.size() == 0)){
			CPrint.line("Invalid or insufficient options. Possible commandline options are:\n");
			CPrint.line("java -jar crococryptfile.jar [OPTIONS] [files & directories]");
			for(ConsoleOptions_Params param: ConsoleOptions_Params.values())
				CPrint.line(" -" + param.name() + ConsoleOptions.description.get(param));
			CPrint.line(" files & dirs\tSingle .croco-archive (decrypt)|files and directories (encrypt)");
			CPrint.line("\nReleased under the GPLv3 by Frank Hissen (www.frankhissen.de)");
			return;
		}
		
		if(check_forceparams && (inputparams == null || inputparams.filesanddirs == null || inputparams.filesanddirs.size() == 0)){
			check_forceparams = false;

			if(UICenter.isGUI()){
				cur = STATE.loadparams;
				CrocoparamsDialog.requestParams(this);
			}
			else{
				UICenter.message("Invalid or insufficient options");
			}
		}
		else if(check_forcedec && !inputparams.decmode && inputparams.filesanddirs.size() == 1 && inputparams.filesanddirs.get(0).isFile()
				&& inputparams.filesanddirs.get(0).getName().toLowerCase().endsWith(SUITES.FILEEXTENSIONwDOT)){
			check_forcedec = false;

			if(UICenter.isGUI()){
				cur = STATE.dec4enc;
				SimpleDialogs.questionYesNo(_T.FileSelection_dec4enc.val(), successcb);
			}
			else{
				main();
			}
		}
		else{
			if(inputparams.decmode){
				if(UICenter.isGUI()){
					File startDest = inputparams.destination;
					if(startDest == null){
						File file = inputparams.filesanddirs.get(0);
						String tmp = file.getName();
						if(tmp.indexOf(".") > 0) tmp = tmp.substring(0, tmp.lastIndexOf("."));
						else tmp = tmp + "Decrypt";
						startDest = new File(file.getParentFile(), tmp);
					}

					Decrypt dec = new Decrypt(this);
					dec.setFolder(startDest);
					PageLauncher.launch(dec);
				}
				else{
					try {
						File croco = inputparams.filesanddirs.get(0);
						inputparams.suite = SUITES.read(croco);
						if(inputparams.suite == null){
							UICenter.message("Input parameters are invalid (no valid provider)");
						}
						else{
							prepareDecryptParams(inputparams.suite);
							setRawParams(inputparams.rawcreds);
							retrieveOperationParams();
						}
					} catch (Exception e) {
						UICenter.message("Input parameters are invalid");
					}
				}
			}
			else{
				if(UICenter.isGUI()){
					File startDest = inputparams.destination;
					if(startDest == null && inputparams.singlesource){
						startDest = inputparams.filesanddirs.get(0);
						if(OSFolders.isRoot(startDest)) startDest = new File(startDest, _T.EncryptWindow_EncryptedFile + BasicFileinfo.FILEEXTENSIONwDOT);
						else if(OSFolders.isRoot(startDest.getParentFile())) startDest = new File(startDest.getParentFile(), _T.EncryptWindow_EncryptedFile + BasicFileinfo.FILEEXTENSIONwDOT);
						else startDest = new File(startDest.getParentFile(), startDest.getParentFile().getName() + BasicFileinfo.FILEEXTENSIONwDOT);
					}
					Encrypt enc = new Encrypt(this, inputparams, startDest);
					PageLauncher.launch(enc);
				}
				else{
					if(inputparams.suite != null){
						prepareEncryptParams(inputparams.suite);
						setRawParams(inputparams.rawcreds);
						retrieveOperationParams();
					}
					else{
						UICenter.message("No provider specified, valid providers are: " + SUITES.providerListAsLine());
					}
				}
			}
		}
	}
	
	
	@Override
	public void callbackValue(Object source, Object ret) {
		try {
			switch (cur) {
			case loadparams:
				inputparams = (CrococryptParameters) ret;
				cur = STATE.none;
				main();
				break;
				
			case dec4enc:
				if(ret == SUCCESS.TRUE)
					inputparams.decmode = true;
				else
					inputparams.decmode = false;
				cur = STATE.none;
				main();
				break;
				
			case decrypt:
				cur = STATE.none;
				SUCCESS decsucc = (SUCCESS) ret;
				
				switch (decsucc) {
				case TRUE:
					if(UICenter.isGUI()){
						cur = STATE.decryptfinishoperation;
						SimpleDialogs.message(_T.DecryptWindow_success, successcb);
					}
					else{
						UICenter.message(_T.DecryptWindow_success);
					}
					Page.exitPage(currentSource);

					break;

				case FALSE:
					if(LASTERROR != null){
						UICenter.message(_T.DecryptWindow_failedspecific + "\n" + LASTERROR);
						LASTERROR = null;
					}
					else{
						UICenter.message(_T.DecryptWindow_failedgeneral);
					}
					break;

				case CANCEL:
					UICenter.message(_T.DecryptWindow_cancel);
					Page.exitPage(currentSource);

					break;
				}
				
				break;
				
			case decryptfinishoperation:
				try {
					Desktop.getDesktop().browse(inputparams.destination.toURI());
				} catch (Exception e2) {}
				break;
				
			case encrypt:
				cur = STATE.none;
				SUCCESS encsucc = (SUCCESS) ret;
				
				switch (encsucc) {
				case TRUE:
					UICenter.message(_T.EncryptWindow_success);
					break;

				case FALSE:
					if(LASTERROR != null){
						UICenter.message(_T.EncryptWindow_failedspecific + "\n" + LASTERROR);
						LASTERROR = null;
					}
					else{
						UICenter.message(_T.EncryptWindow_failedgeneral);
					}
					break;

				case CANCEL:
					UICenter.message(_T.EncryptWindow_cancel);
					break;
				}
				
				Page.exitPage(currentSource);

				break;
				
			case encryptfinishoperation:
				break;
				
			case operationparam:
				setOperationParam(source, ret);
				retrieveOperationParams();
				break;

			default:
				UICenter.message("Internal error: unknown callback response - " + cur);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-98);
		}
	}

	
	@Override
	public void callbackDecrypt(Object source, File destinationFolder) {
		currentSource = source;
		inputparams.destination = destinationFolder;
		File croco = inputparams.filesanddirs.get(0);
		inputparams.suite = SUITES.read(croco);
		
		if(inputparams.suite == null){
			UICenter.message(_T.DecryptWindow_unknownfile);
			return;
		}
		
		prepareDecryptParams(inputparams.suite);
		retrieveOperationParams();
	}

	@Override
	public void callbackEncrypt(Object source, File destination, SUITES provider) {
		if(provider == null){
			UICenter.message("Unknown Operation");
			return;
		}
		
		currentSource = source;
		inputparams.destination = destination;
		inputparams.suite = provider;
		
		prepareEncryptParams(provider);
		retrieveOperationParams();
	}
	
	private void prepareEncryptParams(SUITES suite){
		reqparams = suite.getEncryptParameters();
		reqparams_index = 0;
		operationParams.clear();
	}
	
	private void prepareDecryptParams(SUITES suite){
		reqparams = suite.getDecryptParameters();
		reqparams_index = 0;
		operationParams.clear();
	}
	
	@SuppressWarnings("unchecked")
	private void retrieveOperationParams(){
		if(reqparams == null || reqparams.length <= reqparams_index){
			if(inputparams.decmode){
				cur = STATE.decrypt;
				doDecrypt();
			}
			else{
				cur = STATE.encrypt;
				doEncrypt();
			}
		}
		else{
			cur = STATE.operationparam;
			SuitePARAM theparam = reqparams[reqparams_index];
			reqparams_index++;
			
			if(inputparams.decmode){
				switch (theparam) {
				case password:
					if(UICenter.isGUI())
						new PasswordInputdialog(currentSource).main(this);
					else
						new PasswordInputConsole().main(this);
					break;

				default:
					UICenter.message("Internal error: unknown parameter");
				}
			}
			else{
				switch (theparam) {
				case password:
					if(UICenter.isGUI())
						new PasswordCreatedialog(currentSource).main(this);
					else
						new PasswordCreateConsole().main(this);
					break;
					
				case capi_alias:
					if(UICenter.isGUI()){
						CAPI_DNList dnlist = new CAPI_DNList(this);
						PageLauncher.launch(new Options(currentSource), dnlist);
					}
					break;

				default:
					UICenter.message("Internal error: unknown parameter");
				}
			}
		}
	}
	
	
	private void setOperationParam(Object source, Object value){
		if(source == null || value == null) return;
		
		Class c = null;
		if(source instanceof Class) c = (Class) source;
		else c = source.getClass();
		
		if(c == PasswordCreatedialog.class || c == PasswordCreateConsole.class || c == PasswordInputdialog.class || c == PasswordInputConsole.class){
			operationParams.put(SuitePARAM.password, value);
		}
		else if(c == CAPI_DNList.class){
			operationParams.put(SuitePARAM.capi_alias, value);
		}
	}
	
	private void setRawParams(ArrayList<String> raw){
		if(reqparams == null || reqparams.length == 0 || raw == null || raw.size() != reqparams.length) return;

		for(reqparams_index = 0; reqparams_index < reqparams.length; reqparams_index++){
			SuitePARAM tmp = reqparams[reqparams_index];
			switch (tmp) {
			case capi_alias:
				operationParams.put(tmp, raw.get(reqparams_index));
				continue;

			case password:
				UICenter.message("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
						"You are providing a password as commandline option, this is NOT RECOMMEND" +
						"\nsince your password might be easily gathered." +
						"\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				char[] pw = raw.get(reqparams_index).toCharArray();
				if(pw.length < CryptoCodes.STANDARD_PBKDF2_PWLEN){
					CPrint.line(_T.PasswordEncrypt_len.msg(CryptoCodes.STANDARD_PBKDF2_PWLEN));
					continue;
				}
				operationParams.put(tmp, pw);
				continue;

			default:
				UICenter.message("unknown credential");
				break;
			}
		}
	}

	private void doEncrypt(){
		Suite instance = Suite.getInitializedInstance(inputparams.suite, SuiteMODE.ENCRYPT, operationParams);
		StatusUpdate stat = null;
		if(UICenter.isGUI()){
			stat = new ProgressDoubleWindow(((Page)currentSource).getPageLauncher().getWindow()).prepare();
		}
		else if(inputparams.verboseconsole){
			stat = new StatusConsole();
		}
		
		new CrocoFilewriter(successcb, instance, inputparams.filesanddirs.toArray(new File[]{}), inputparams.destination).execute(stat);
	}
	
	private void doDecrypt(){
		Suite instance = Suite.getInitializedInstance(inputparams.suite, SuiteMODE.DECRYPT, operationParams);
		StatusUpdate stat = null;
		if(UICenter.isGUI()){
			stat = new ProgressWindow(((Page)currentSource).getPageLauncher().getWindow()).prepare();
		}
		else if(inputparams.verboseconsole){
			stat = new StatusConsole();
		}
		new CrocoFilereader(successcb, instance, inputparams.filesanddirs.get(0), inputparams.destination).execute(stat);
	}
}
