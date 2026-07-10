import * as cdk from "aws-cdk-lib";
import * as apigwv2 from "aws-cdk-lib/aws-apigatewayv2";
import { HttpLambdaIntegration } from "aws-cdk-lib/aws-apigatewayv2-integrations";
import * as acm from "aws-cdk-lib/aws-certificatemanager";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as logs from "aws-cdk-lib/aws-logs";
import * as route53 from "aws-cdk-lib/aws-route53";
import { CloudFrontTarget } from "aws-cdk-lib/aws-route53-targets";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as ssm from "aws-cdk-lib/aws-ssm";
import { Construct } from "constructs";
import { STAGES } from "./ci-stack.js";

export type Stage = (typeof STAGES)[number];

export interface AppStackProps extends cdk.StackProps {
  stage: Stage;
  /** Path to the assembled api Lambda jar */
  apiJarPath: string;
  /** Path to the built frontend (the contents of frontend/dist) */
  webrootPath: string;
}

/**
 * Per-stage application stack: CloudFront serving the SPA from S3 with
 * /api/* routed to the HTTP API in front of the api Lambda.
 *
 * Domain name, hosted zone id, and certificate ARN are resolved from SSM
 * at deploy time (see docs/bootstrap.md), so they never appear in the
 * synthesized template or the snapshots.
 */
export class AppStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: AppStackProps) {
    super(scope, id, props);
    const { stage, apiJarPath, webrootPath } = props;

    const domainName = ssm.StringParameter.valueForStringParameter(
      this,
      `/football-blackjack/${stage}/domain-name`,
    );
    const hostedZoneId = ssm.StringParameter.valueForStringParameter(
      this,
      `/football-blackjack/${stage}/hosted-zone-id`,
    );
    const certificateArn = ssm.StringParameter.valueForStringParameter(
      this,
      `/football-blackjack/${stage}/certificate-arn`,
    );

    // api Lambda + HTTP API

    const apiFunction = new lambda.Function(this, "ApiFunction", {
      runtime: lambda.Runtime.JAVA_21,
      handler: "com.adamnfish.fbj.api.ApiLambda::handleRequest",
      code: lambda.Code.fromAsset(apiJarPath),
      memorySize: 1024,
      timeout: cdk.Duration.seconds(10),
      logGroup: new logs.LogGroup(this, "ApiFunctionLogs", {
        retention: logs.RetentionDays.ONE_MONTH,
      }),
    });

    const httpApi = new apigwv2.HttpApi(this, "HttpApi");
    httpApi.addRoutes({
      path: "/api/{operation}",
      methods: [apigwv2.HttpMethod.POST],
      integration: new HttpLambdaIntegration("ApiIntegration", apiFunction),
    });

    // webroot bucket + CloudFront

    const webrootBucket = new s3.Bucket(this, "Webroot", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      enforceSSL: true,
    });

    // SPA fallback: rewrite extensionless requests (client-side routes) to
    // index.html. A viewer-request function on the S3 behaviour only, rather
    // than distribution-wide error responses, so /api/* error responses are
    // never rewritten
    const spaRewrite = new cloudfront.Function(this, "SpaRewrite", {
      code: cloudfront.FunctionCode.fromInline(
        `function handler(event) {
  var request = event.request;
  if (!request.uri.includes(".")) {
    request.uri = "/index.html";
  }
  return request;
}`,
      ),
      runtime: cloudfront.FunctionRuntime.JS_2_0,
    });

    // The HTTP API origin wants the bare execute-api hostname
    const apiDomain = cdk.Fn.select(2, cdk.Fn.split("/", httpApi.apiEndpoint));

    const distribution = new cloudfront.Distribution(this, "Distribution", {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(webrootBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        functionAssociations: [
          {
            function: spaRewrite,
            eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
          },
        ],
      },
      additionalBehaviors: {
        "/api/*": {
          origin: new origins.HttpOrigin(apiDomain),
          viewerProtocolPolicy:
            cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          // Forward the viewer request as-is, minus the Host header, which
          // must be the execute-api hostname for API Gateway to route
          originRequestPolicy:
            cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
        },
      },
      defaultRootObject: "index.html",
      domainNames: [domainName],
      certificate: acm.Certificate.fromCertificateArn(
        this,
        "Certificate",
        certificateArn,
      ),
    });

    // Publish the frontend build and invalidate CloudFront so a cached
    // index.html can't hide a new release
    new s3deploy.BucketDeployment(this, "PublishWebroot", {
      sources: [s3deploy.Source.asset(webrootPath)],
      destinationBucket: webrootBucket,
      distribution,
      distributionPaths: ["/*"],
    });

    // Stage domain DNS. The L1 record set group is deliberate: the L2
    // ARecord does synth-time string logic on record names, which silently
    // misbehaves on deploy-time tokens like the SSM-resolved domain name
    new route53.CfnRecordSetGroup(this, "DnsRecords", {
      hostedZoneId,
      recordSets: ["A", "AAAA"].map((type) => ({
        name: domainName,
        type,
        aliasTarget: {
          dnsName: distribution.distributionDomainName,
          hostedZoneId: CloudFrontTarget.CLOUDFRONT_ZONE_ID,
        },
      })),
    });

    new cdk.CfnOutput(this, "DistributionDomainName", {
      value: distribution.distributionDomainName,
      description: "CloudFront domain, the alias target for the stage domain",
    });
  }
}
