function googleCloudBuildExecute() {
            var machineTypeValue = document.querySelector("#machineType").value;
            var formObj;

            var argsValue = ["-V"];
            if (document.getElementById("enableWebhook").checked && currentBuildObj.webhook !== undefined && currentBuildObj.webhook !== null
                    && currentBuildObj.webhook !== "") {
                argsValue = ["-s", "-I", currentBuildObj.webhook.replace("{image}", currentBuildObj.registry)];
            }

            if (currentBuildObj.sourceType === "Directory" || currentBuildObj.sourceType === "GCSBucket") {
                var sSource = {
                    "storageSource": {
                        "bucket": "build-image-cache",
                        "object": "dir-to-tgz/" + currentBuildObj.id + ".tgz"
                    }
                };
                if (currentBuildObj.sourceType === "GCSBucket") {
                    var gsUrlArray = currentBuildObj.buildContext.split("/");
                    if (gsUrlArray.length > 3) {
                        var gsUrl = currentBuildObj.buildContext;
                        var pathStartIndex = gsUrl.indexOf("/", 5);
                        var bucketValue = gsUrl.substring(5, pathStartIndex);
                        var objectValue = gsUrl.substring(pathStartIndex + 1);
                        sSource = {
                            "storageSource": {
                                "bucket": bucketValue,
                                "object": objectValue
                            }
                        };
                    } else {
                        alert("GCS URL格式不对！");
                        return;
                    }
                }
                formObj = {
                    "tags": [
                        currentBuildObj.id
                    ],
                    "source": sSource,
                    "timeout": "1200s",
                    "steps": [
                        {
                            "name": "gcr.io/cloud-builders/docker",
                            //"timeout": "1200s",
                            "args": [
                                "build",
                                "-t",
                                currentBuildObj.registry,
                                "."
                            ]
                        }, {
                            "name": "gcr.io/cloud-builders/docker",
                            "args": ["push", currentBuildObj.registry]
                        }, {
                            "name": "curlimages/curl",
                            "args": argsValue
                        }
                    ],
                    "options": {
                        "machineType": machineTypeValue
                    }
//                    ,"images": [
//                        currentBuildObj.registry
//                    ]
                };
            } else if (currentBuildObj.sourceType === "GitRepository") {
                //  git://github.com/pawarvishal123/docker-helloworld.git
                formObj = {
                    "tags": [
                        currentBuildObj.id
                    ],
                    "timeout": "1200s",
                    "steps": [
                        {
                            "name": "gcr.io/cloud-builders/git",
                            "args": ["clone", currentBuildObj.buildContext, "source-root"]
                        },
                        {
                            "name": "gcr.io/cloud-builders/docker",
                            "args": [
                                "build",
                                "-t",
                                currentBuildObj.registry,
                                "."
                            ],
                            //"timeout": "1200s",
                            "dir": "source-root"
                        },
                        {
                            "name": "gcr.io/cloud-builders/docker",
                            "args": ["push", currentBuildObj.registry]
                        },
                        {
                            "name": "curlimages/curl",
                            "args": argsValue
                        }
                    ],
                    "options": {
                        "machineType": machineTypeValue
                    }
//                    ,"images": [
//                        currentBuildObj.registry
//                    ]
                };
            }
            console.log(formObj);
            if (formObj !== null && formObj !== undefined) {
                fetch(backendBaseURI + "/-/api/ga/authed/r/build-task/gcp-build", {
                    method: "POST",
                    body: JSON.stringify(formObj),
                    headers: {
                        "Content-Type": "application/json",
                        "Authorization": "Bearer " + getIdToken()
                    }
                }).then(function (r) {
                    if (r.ok) {
                        r.json().then(data => {
                            console.log(data);
                            //buildId = data.name;

                            var fd = new FormData();
                            fd.append("latestBuildPlatform", "GoogleCloudBuild");
                            fd.append("latestBuildTaskId", data.name);
                            fetch(backendBaseURI + "/-/api/ga/authed/r/image-scripts/" + currentBuildObj.id + "/update-latest-build", {
                                method: "PUT", body: fd,
                                headers: {
                                    'Authorization': 'Bearer ' + getIdToken()
                                }
                            }).then(r => r.text()).then(data => {
                                console.log(data);
                            });

                            alert("googleCloudBuild Op Name - " + data.name);
                        });
                    }
                });
            } else {
                alert("未定义请求负载！");
            }
        }
