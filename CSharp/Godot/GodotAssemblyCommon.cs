using Godot;
using System;
using System.Reflection;
public class GodotAssemblyCommon
{

    //  include_filter="our-files/android/pcks/*"
    static String[] pckPath = new String[] { "res://our-files/android/pcks/breakout.pck", "res://Breakout.tscn", "res://.mono/assemblies/Debug/Breaker.dll" };
    //String[] pckPath = new String[] { "res://our-files/android/pcks/g2.pck", "res://Control.tscn", "res://.mono/assemblies/Debug/g2.dll" };
    async public static void loadPCK(SceneTree tree)
    {
        GD.Print("LoadResourcePack - " + pckPath[0]);
        var ok = ProjectSettings.LoadResourcePack(pckPath[0]); // 会检查支持的PCK版本 - Pack version unsupported: 1.
        GD.Print(ok);
        if (ok)
        {
            GD.Print("CrossAssembly");
            var sceneNode = (Node)GodotAssemblyCommon.CrossAssembly(pckPath[1], pckPath[2]);
            GD.Print("CrossAssembly over.");

            //	关键 - 不加后续可能闪退
            await tree.ToSignal(tree.CreateTimer(0.5f), "timeout");

            tree.CurrentScene.QueueFree();
            GD.Print("AddChild");
            tree.Root.AddChild(sceneNode);
            GD.Print("CurrentScene = sceneNode");
            tree.CurrentScene = sceneNode;
        }
    }

    static Godot.Collections.Dictionary jsonDict;
    static System.Collections.Generic.Dictionary<String, Type> dictScripts =
    new System.Collections.Generic.Dictionary<String, Type>();

    static Assembly asm = null;
    //	todo - 如果dll载入路径不同或者bytes方式多个Assembly实例均会出现无法类型转换问题。
    public static void LoadDLL(String dllPath)
    {
        if (FileAccess.FileExists(dllPath))
        {
            byte[] rawAssembly = FileAccess.GetFileAsBytes(dllPath);
            asm = AppDomain.CurrentDomain.Load(rawAssembly);
            //var asm = Assembly.LoadFile(OS.GetUserDataDir() + @"/Breaker.dll");
            GD.Print("反射该dll时应指明程序集名 - " + asm.GetName());
        }
        else
        {
            GD.Print("dllPath not is exists - " + dllPath);
        }

        // var dllFile = new Godot.File();
        // if (dllFile.FileExists(dllPath))
        // {
        //     dllFile.Open(dllPath, Godot.File.ModeFlags.Read);
        //     byte[] rawAssembly = dllFile.GetBuffer((int)dllFile.GetLen());
        //     dllFile.Close();
        //     asm = AppDomain.CurrentDomain.Load(rawAssembly);
        //     //var asm = Assembly.LoadFile(OS.GetUserDataDir() + @"/Breaker.dll");
        //     GD.Print("反射该dll时应指明程序集名 - " + asm.GetName());
        // }
        // else
        // {
        //     GD.Print("dllPath not is exists - " + dllPath);
        // }

        var pDirName = System.IO.Path.GetDirectoryName(dllPath).GetFile();
        String scriptsMetadata = null;


        var scriptsMetadataPath = "res://.mono/metadata/scripts_metadata." + pDirName.ToLower();
        GD.Print(scriptsMetadataPath);

        //	scripts_metadata.*文件亲测会被PCK覆盖，Release版本是否影响主程序待测？
        if (FileAccess.FileExists(scriptsMetadataPath))
        {
            scriptsMetadata = FileAccess.GetFileAsString(scriptsMetadataPath);
            GD.Print(scriptsMetadata);
            var json = Json.ParseString(scriptsMetadata);
            jsonDict = json.As<Godot.Collections.Dictionary>();
        }
        else
        {
            GD.Print("scriptsMetadataPath not is exists - " + scriptsMetadataPath);
        }

        // var f = new Godot.File(); //	scripts_metadata.*文件亲测会被PCK覆盖，Release版本是否影响主程序待测？
        // var scriptsMetadataPath = "res://.mono/metadata/scripts_metadata." + pDirName.ToLower();
        // GD.Print(scriptsMetadataPath);
        // if (f.FileExists(scriptsMetadataPath))
        // {
        //     if (f.Open(scriptsMetadataPath, File.ModeFlags.Read) == Error.Ok)
        //     { //  若读取换为 File.ModeFlags.Read
        //         scriptsMetadata = f.GetAsText();
        //         f.Close();
        //     }
        //     GD.Print(scriptsMetadata);
        //     var json = JSON.Parse(scriptsMetadata);
        //     jsonDict = json.Result as Godot.Collections.Dictionary;
        // }
        // else
        // {
        //     GD.Print("scriptsMetadataPath not is exists - " + scriptsMetadataPath);
        // }
    }

    //   Bug - 临时解决PCK dll类未绑定至tscn场景问题 https://github.com/godotengine/godot/issues/36828
    //   var ok = ProjectSettings.LoadResourcePack("res://other/temp.zip"); // 或x.zip
    //	LoadDLL("res://.mono/assemblies/Debug/g2.dll");
    //	GodotAssemblyCommon.CrossAssembly("res://Control.tscn", "res://.mono/assemblies/Debug/g2.dll");
    /*	// PCK项目内载入场景用法：
			//	声明 - Func<Node> instance; 用法 - instance();
			//	scene.Duplicate()在Free()后副本化极慢，估计是Script实例为同一引用所致。
			var m = Type.GetType("GodotAssemblyCommon,PopulationControl");
			if (m != null)
			{
				instance = () =>
				{
					var fix = m.GetMethod("CrossAssembly", BindingFlags.Public | BindingFlags.Static);
					var ni = (Node)fix.Invoke(null, new object[] { "res://brick.tscn", "res://.mono/assemblies/Debug/Breaker.dll" });
					return ni;
				};
			}
			else
			{
				instance = () =>
				{
					var bps = ResourceLoader.Load("res://brick.tscn") as PackedScene;
					var ni = (Node)bps.Instance();
					return ni;
				};
			}
	*/
    /*
		亲测：
			PCK在IDE中生成*.tscn的Button信号pressed即回调可用(支持嵌套)。
			PCK动态连接内置信号可用(支持嵌套) - this.Connect("pressed", this, nameof(_on_Button_pressed));
			含*.cs类的场景载入及ResourceLoader.Load("res://x.tscn")必须换成自建库方式；
			Assembly.LoadFile返回的程序集必须单例完全相同，否则无法转换类型为自定义类型。
			属性方式也可用 - [Signal] public delegate void BrickDestroyed();
		新思路 - 
			首次构造好后单例化至约定目录？ ResourceSaver.save("res://path/name.scn", scene)
			replace *.tscn 的所有 res://ControlClass.cs 至主dll内建的1百个继承类 res://PCK_GEN_CLASS_1.cs
			或者 仅仅用做消除报错 - res://PCK_GEN_CLASS_FIX.cs
	*/
    public static GodotObject CrossAssembly(String tscnPath, String dllPath)
    {
        if (asm == null)
        {
            //	todo - 后续考虑线程安全
            LoadDLL(dllPath);
            //dictScripts.Add("res://S2.cs", asm.GetType("g2.S2"));
        }

        // //	获取该场景所有C#脚本Path
        // foreach (string dependencyPath in ResourceLoader.GetDependencies(tscnPath))
        // { }

        //	每个同名tscn场景首次实例化时，解包*.zip耗时15秒，*.pck则仅用时3秒。
        //  优化点 - 可先异步实例化一次以避免阻塞UI进度提示，再次实例化时再使用。
        var psps = ResourceLoader.Load<PackedScene>(tscnPath);
        //	首次实例化报错暂时忽略(try无法捕获)，SetScript后就正确了。
        Node ps = psps.Instantiate();   //	todo - 根节点是否可绑定脚本？
        ulong psId = ps.GetInstanceId();

        //	后续改为直接在ps实例中遍历
        for (int i = 0; i < psps.GetState().GetNodeCount(); i++)
        {   //  PackedScene节点GetNodeType返回值为空字符串
            var currentNodePath = psps.GetState().GetNodePath(i);
            GD.Print(currentNodePath + "(" + psps.GetState().GetNodeType(i) + "):");
            var psNode = psps.GetState().GetNodeInstance(i);    //  非PackedScene节点则为null
                                                                //GD.Print(psNode);
            if (psNode != null)
            {   //	PackedScene节点暂未处理
                GD.Print("psNode != null." + currentNodePath);
                //var pni = psNode.Instance();
                //GD.Print("psNode != null Name." + pni.Name);
                //var rootScene = (Node)GD.InstanceFromId(psId);
                //GD.Print("psNode != null GetNode." + rootScene.GetNode(currentNodePath));
                //var button = rootScene.GetNode(currentNodePath).GetNode("Button");
                //GD.Print("psNode != null GetNode Button." + button);
                //var script = Activator.CreateInstance(dictScripts["res://S2.cs"]);
                //button.SetScript(((Godot.Object)script).GetScript());
                GD.Print("ps node over.");

                NestedFixDependencies(psId, currentNodePath, psNode);
            }
            else
            {
                GD.Print("GetNodeProperty");
                for (int j = 0; j < psps.GetState().GetNodePropertyCount(i); j++)
                {
                    var pn = psps.GetState().GetNodePropertyName(i, j);
                    //GD.Print(pn);
                    if (pn == "script")
                    {
                        var pv = (CSharpScript)psps.GetState().GetNodePropertyValue(i, j);
                        GD.Print(pv.ResourcePath);  //	res://ControlClass.cs
                                                    //	Makes sure it's a C# script
                        if (pv.ResourcePath.StartsWith("res://") && pv.ResourcePath.EndsWith(".cs"))
                        {
                            var obj = jsonDict[pv.ResourcePath].As<Godot.Collections.Dictionary>();
                            obj = obj["class"].As<Godot.Collections.Dictionary>();
                            var cn = obj["class_name"].ToString();
                            var ns = obj["namespace"];
                            var fullClassName = ns.ToString().Length > 0 ? ns + "." + cn : cn;
                            GD.Print("ns.cn - " + fullClassName);

                            if (!dictScripts.ContainsKey(pv.ResourcePath))
                            {
                                // Look for each type in the assembly of the DLC
                                foreach (Type type in asm.GetTypes())
                                {
                                    //	v4.0+判断[ScriptPathAttribute("res://Player.cs")]？
                                    if (type.ToString().Equals(fullClassName))
                                    {
                                        // Try to instance the script with Activator
                                        //object scriptInstance = Activator.CreateInstance(type);
                                        //var control = (Godot.Object)scriptInstance;
                                        dictScripts.Add(pv.ResourcePath, type);
                                        break;
                                    }
                                }
                            }

                            var scriptClass = Activator.CreateInstance(dictScripts[pv.ResourcePath]);
                            GD.Print(scriptClass);
                            var rootScene = (Node)GodotObject.InstanceFromId(psId);
                            var xNode = rootScene.GetNode(currentNodePath);
                            xNode.SetScript(((GodotObject)scriptClass).GetScript());
                        }
                    }
                }
            }
        }

        //  初测 - 如果子node.SetScript(s)也会触发根场景释放。
        var psNew = GodotObject.InstanceFromId(psId);
        //GD.Print(psNew);
        GD.Print("FixDependencies over. " + psNew.GetClass());
        return (GodotObject)psNew;
    }

    public static void NestedFixDependencies(ulong psId, String currentNodePathByRoot, PackedScene psps)
    {
        //	后续改为直接在ps实例中遍历
        for (int i = 0; i < psps.GetState().GetNodeCount(); i++)
        {   //  PackedScene节点GetNodeType返回值为空字符串
            var currentNodePath = psps.GetState().GetNodePath(i);
            GD.Print(currentNodePath + "(" + psps.GetState().GetNodeType(i) + "):");
            var psNode = psps.GetState().GetNodeInstance(i);    //  非PackedScene节点则为null
            GD.Print(psNode);
            GD.Print("NestedFixDependencies GetNodeProperty");
            if (psNode != null)
            {   //	PackedScene节点暂未处理
                GD.Print("psNode != null." + currentNodePath);
            }
            else
            {
                for (int j = 0; j < psps.GetState().GetNodePropertyCount(i); j++)
                {
                    var pn = psps.GetState().GetNodePropertyName(i, j);
                    //GD.Print(pn);
                    if (pn == "script")
                    {
                        var pv = (CSharpScript)psps.GetState().GetNodePropertyValue(i, j);
                        GD.Print(pv.ResourcePath);  //	res://ControlClass.cs
                                                    //	Makes sure it's a C# script
                        if (pv.ResourcePath.StartsWith("res://") && pv.ResourcePath.EndsWith(".cs"))
                        {
                            var obj = jsonDict[pv.ResourcePath].As<Godot.Collections.Dictionary>();
                            obj = obj["class"].As<Godot.Collections.Dictionary>();
                            var cn = obj["class_name"].ToString();
                            var ns = obj["namespace"];
                            var fullClassName = ns.ToString().Length > 0 ? ns + "." + cn : cn;
                            GD.Print("ns.cn - " + fullClassName);

                            if (!dictScripts.ContainsKey(pv.ResourcePath))
                            {
                                // Look for each type in the assembly of the DLC
                                foreach (Type type in asm.GetTypes())
                                {
                                    //	v4.0+判断[ScriptPathAttribute("res://Player.cs")]？
                                    if (type.ToString().Equals(fullClassName))
                                    {
                                        // Try to instance the script with Activator
                                        //object scriptInstance = Activator.CreateInstance(type);
                                        //var control = (Godot.Object)scriptInstance;
                                        dictScripts.Add(pv.ResourcePath, type);
                                        break;
                                    }
                                }
                            }

                            var scriptClass = Activator.CreateInstance(dictScripts[pv.ResourcePath]);
                            GD.Print(scriptClass);
                            var rootScene = (Node)GodotObject.InstanceFromId(psId);
                            var xNode = rootScene.GetNode(currentNodePathByRoot).GetNode(currentNodePath);
                            xNode.SetScript(((GodotObject)scriptClass).GetScript());
                        }
                    }
                }
            }
        }
    }

    // public static System.Collections.Generic.KeyValuePair<GodotObject, String> LoadAndroidPlugin(String pluginName)
    // {
    //     GodotObject plugin = null;
    //     String msg = null;
    //     if (OS.HasFeature("Android"))
    //     {
    //         if (Engine.HasSingleton(pluginName))
    //         {
    //             var p = Engine.GetSingleton(pluginName);
    //             if (p != null)
    //             {
    //                 plugin = p;
    //             }
    //             else
    //             {
    //                 msg = (pluginName + " is null!");
    //             }
    //         }
    //         else
    //         {
    //             msg = ("HasSingleton " + pluginName);
    //         }
    //     }
    //     else
    //     {
    //         msg = ("Not Support this Plugin in this platform!");
    //     }
    //     return new KeyValuePair<GodotObject, String>(plugin, msg);
    // }
}
